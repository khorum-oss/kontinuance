package org.khorum.oss.kontinuance.server.service

import java.nio.file.Files
import java.nio.file.Path

/** Real git repositories for the publishing tests: a source with history, and a bare remote to push to. */
object GitFixture {

    /** Runs git in [dir] and returns its trimmed output; a non-zero exit fails the test. */
    fun git(dir: Path, vararg args: String): String {
        val argv = listOf("git", "-c", "user.name=test", "-c", "user.email=test@example.invalid", "-c", "commit.gpgsign=false") + args
        val process = ProcessBuilder(argv).directory(dir.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output
    }

    /**
     * A repository at `<base>/<owner>/<name>.git` — where a binding with `cloneBase: <base>` clones from — on
     * `main`. Its first commit names the repository, so two sources made in the same second never share a commit id.
     */
    fun source(base: Path, owner: String, name: String): Path =
        Files.createDirectories(base.resolve(owner).resolve("$name.git")).also {
            git(it, "init", "--quiet", "-b", "main")
            commit(it, "first commit of $owner/$name")
        }

    /** An empty bare repository standing in for the publish remote. */
    fun remote(dir: Path): Path = Files.createDirectories(dir).also { git(it, "init", "--quiet", "--bare", "-b", "main") }

    /** Commits on the current branch and returns the new commit's id. */
    fun commit(repo: Path, message: String): String {
        git(repo, "commit", "--quiet", "--allow-empty", "-m", message)
        return git(repo, "rev-parse", "HEAD")
    }

    /** Every ref in [repo], as `name -> commit`. */
    fun refs(repo: Path): Map<String, String> =
        git(repo, "for-each-ref", "--format=%(refname) %(objectname)").lines().filter { it.isNotBlank() }
            .associate { it.substringBefore(' ') to it.substringAfter(' ') }
}
