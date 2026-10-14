package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Publishes with `git` itself (043): fetch the tracked branch from the repository's source into a bare
 * scratch repository, then push **one commit to one ref** on the publish remote.
 *
 * ```
 * git fetch <source> +refs/heads/<trackedBranch>:refs/kontinuance/source
 * git push  <remote> <sha>:refs/heads/<branch>
 * ```
 *
 * The refspec names a single commit and a single ref, so nothing but the tracked branch can leave a
 * private source — by construction, not by filter. The scratch repository only ever holds the tracked
 * branch, and the commit must be on it as just fetched or nothing is pushed.
 *
 * The push is never forced. A remote that has a commit the source does not refuses it, and that refusal
 * is the right answer: it needs a person, not a retry.
 *
 * Credentials reach `git` through a credential helper that reads the child process's environment. A
 * token is never part of a URL or an argument, so it cannot end up in `ps`, a reflog or an error message.
 * The host's own helpers (a keychain) are switched off for these commands: what a publish may push with
 * is this configuration and nothing a logged-in user happens to have stored.
 */
@Component
class GitPublisher(
    @Value("\${kontinuance.publish.work-dir}") workDir: String,
    @param:Value("\${kontinuance.publish.token:}") private val pushToken: String,
    @param:Value("\${kontinuance.hooks.gitea.token:}") private val fetchToken: String,
) : CommitPublisher {
    private val workDir: Path = Path.of(workDir)

    override suspend fun publish(binding: RepositoryBinding, sha: String): Result<Unit> = withContext(Dispatchers.IO) {
        val target = binding.publish ?: return@withContext failure("${binding.repo.slug} has no publish target")
        // The sha becomes part of a refspec; anything but a full commit id is refused before git sees it.
        if (!SHA.matches(sha)) return@withContext failure("'$sha' is not a full commit id")
        val source = "refs/heads/${binding.trackedBranch}"
        try {
            val repo = scratch(binding)
            git(repo, fetchToken, "fetch", "--quiet", "--no-tags", binding.cloneUrl, "+$source:$FETCHED").onFailure {
                return@withContext failure("could not fetch ${binding.trackedBranch} from the source: ${it.message}")
            }
            git(repo, NO_TOKEN, "merge-base", "--is-ancestor", sha, FETCHED).onFailure {
                return@withContext failure("$sha is not on ${binding.trackedBranch}; nothing was pushed")
            }
            git(repo, pushToken, "push", target.remote, "$sha:refs/heads/${target.branch}").onFailure {
                return@withContext failure("the remote refused the push to ${target.branch}: ${it.message}")
            }
            Result.success(Unit)
        } catch (e: IOException) {
            failure("could not run git: ${e.message}")
        }
    }

    /** The bare repository this binding publishes through, created on first use. */
    private fun scratch(binding: RepositoryBinding): Path {
        val repo = workDir.resolve(binding.repo.owner).resolve("${binding.repo.name}.git")
        if (!repo.resolve("HEAD").exists()) {
            Files.createDirectories(repo)
            git(repo, NO_TOKEN, "init", "--quiet", "--bare").getOrElse { throw IOException("git init failed: ${it.message}") }
        }
        return repo
    }

    /** Runs one git command in [repo]; a failure carries the line of git's output that says why. */
    private fun git(repo: Path, token: String, vararg args: String): Result<Unit> {
        val argv = buildList {
            add("git")
            // An empty value clears every helper configured on the host before ours is added.
            addAll(listOf("-c", "credential.helper="))
            if (token.isNotBlank()) addAll(listOf("-c", "credential.helper=$HELPER"))
            addAll(args)
        }
        val output = Files.createTempFile("kontinuance-git", ".log")
        try {
            val process = ProcessBuilder(argv).directory(repo.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .apply {
                    environment()["GIT_TERMINAL_PROMPT"] = "0"
                    environment().remove("GIT_ASKPASS")
                    environment().remove("SSH_ASKPASS")
                    if (token.isNotBlank()) environment()[TOKEN_ENV] = token
                }
                .start()
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return failure("git ${args.first()} did not finish within ${TIMEOUT_SECONDS}s")
            }
            if (process.exitValue() == 0) return Result.success(Unit)
            return failure(reason(output.readText(), token) ?: "git ${args.first()} exited ${process.exitValue()}")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted while running git", e)
        } finally {
            Files.deleteIfExists(output)
        }
    }

    /** The line that explains a failure — git's rejection or error if it gave one, else its last line. */
    private fun reason(output: String, token: String): String? {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val line = lines.firstOrNull { line -> TELLING.any { line.contains(it) } } ?: lines.lastOrNull()
        return if (token.isBlank()) line else line?.replace(token, "***")
    }

    private fun failure(reason: String): Result<Unit> = Result.failure(IllegalStateException(reason))

    private companion object {
        val SHA = Regex("[0-9a-f]{40}")
        val TELLING = listOf("[rejected]", "[remote rejected]", "remote: error", "fatal:", "error:")
        const val FETCHED = "refs/kontinuance/source"
        const val NO_TOKEN = ""
        const val TOKEN_ENV = "KONTINUANCE_GIT_PASSWORD"
        const val TIMEOUT_SECONDS = 120L

        // Answers git's `get` with the token from the environment. The user name is a placeholder: both
        // GitHub and Gitea authenticate a token presented as the password.
        const val HELPER =
            "!f() { test \"\$1\" = get && printf 'username=x-access-token\\npassword=%s\\n' \"\$$TOKEN_ENV\"; }; f"
    }
}
