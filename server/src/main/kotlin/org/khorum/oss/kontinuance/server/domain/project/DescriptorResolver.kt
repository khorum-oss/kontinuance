package org.khorum.oss.kontinuance.server.domain.project

import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** Where a resolved descriptor came from. */
enum class Origin { Stored, Repo, Live }

/** The outcome of asking "what pipeline does this project run?" (041, FR-003). */
sealed interface Resolution

/**
 * A parsed pipeline plus, for a repo-hosted descriptor, the commit [sha] it was read from — which the
 * caller pins the checkout to so descriptor and code always come from one commit (FR-004). [text] is the
 * raw source the pipeline was parsed from, kept alongside it so a display surface (the Config screen,
 * 041 FR-008) can show the descriptor that actually resolved rather than re-deriving it from some other
 * file and risking the two disagreeing.
 */
data class Resolved(val pipeline: Pipeline, val sha: String?, val origin: Origin, val text: String) : Resolution

/** Resolution failed. [reason] is operator-facing and names the cause (FR-005). */
data class Rejected(val reason: String) : Resolution

/**
 * Answers "what pipeline does the active project run?" for the manual trigger path.
 *
 * Order (FR-009a): the active project's stored descriptor, else the active project's repository, else
 * the server's live descriptor file, else a rejection. Every failure is a [Rejected] rather than a
 * thrown exception — an invalid descriptor, a missing/unreadable file, an unreachable GitHub API, or a
 * bad token all resolve the same way — so [org.khorum.oss.kontinuance.server.service.RunTrigger] can
 * refuse before a run record exists — a stage-less or missing descriptor must never become a run that
 * starts and then discovers it has nothing to do.
 */
class DescriptorResolver(
    private val projects: ProjectStore,
    private val liveDescriptor: Path,
    private val descriptorPath: String,
    private val clients: GitHubClientProvider,
) {

    suspend fun resolve(): Resolution {
        val active = try {
            projects.activeName()
        } catch (e: IOException) {
            return Rejected("could not read the active project: ${e.message}")
        } ?: return fromLive()

        val stored = try {
            projects.get(active)
        } catch (e: IOException) {
            return Rejected("could not read the stored descriptor for '$active': ${e.message}")
        }
        stored?.let { text ->
            return parse(text, sha = null, origin = Origin.Stored, failurePrefix = "invalid stored descriptor for '$active'")
        }

        val source = projects.source(active) ?: return fromLive()
        return fromRepository(source)
    }

    private suspend fun fromRepository(source: ProjectSource): Resolution {
        // source is only non-null via ProjectStore.source() when it has a real (non-blank) repo URL, so
        // there is no "no repository" case left to reject here.
        val url = source.repo.orEmpty()
        val repo = RepoRef.parse(url)
            ?: return Rejected(
                "repo-hosted descriptors need a GitHub repository ($url is not one) — " +
                    "store a descriptor for this project instead",
            )
        val branch = source.branch?.takeIf { it.isNotBlank() }
            ?: return Rejected("the project's source has no branch — a repo-hosted descriptor needs one")

        return try {
            val client = clients.client()
                ?: return Rejected("no GitHub token available — connect a source or set the token env var")
            val sha = client.branchHead(repo, branch)
                ?: return Rejected("branch '$branch' not found on ${repo.slug}")
            val text = client.fileAt(repo, descriptorPath, sha)
                ?: return Rejected("no $descriptorPath on '$branch' at ${repo.slug}")
            parse(text, sha, Origin.Repo, failurePrefix = "invalid descriptor from ${repo.slug}@$branch:$descriptorPath")
        } catch (e: GitHubApiException) {
            // Reached GitHub, but it said no (bad token, rate limit, server error, ...).
            Rejected("GitHub API returned HTTP ${e.statusCode} for ${repo.slug}")
        } catch (e: IOException) {
            // Never reached GitHub at all (DNS, connection refused, TLS, timeout) — the one cause
            // FR-005 calls out by name, so it must land here rather than escape resolve().
            Rejected("GitHub unreachable — ${e.message}")
        } catch (e: IllegalArgumentException) {
            // The branch is operator-typed and reaches the client as a URL path segment. The REST client
            // encodes it, but this resolver takes any GitHubClient — belt and braces, so a request that
            // still cannot be addressed rejects rather than escaping as a 500.
            Rejected("could not address branch '$branch' on ${repo.slug}: ${e.message}")
        }
    }

    private fun fromLive(): Resolution {
        if (!liveDescriptor.isRegularFile()) return Rejected("no pipeline descriptor at $liveDescriptor")
        val text = try {
            liveDescriptor.readText()
        } catch (e: IOException) {
            return Rejected("could not read $liveDescriptor: ${e.message}")
        }
        return parse(text, sha = null, origin = Origin.Live, failurePrefix = "invalid descriptor at $liveDescriptor")
    }

    /**
     * Parses [text], naming [failurePrefix] (which source it came from) in any rejection (FR-005).
     *
     * Every failure is caught, not just `DescriptorException`: two parser paths raise a bare
     * `IllegalArgumentException` / `NumberFormatException` (an empty `secrets:` entry, a timeout too
     * large for `Long`), and a repository's descriptor is untrusted input, so narrowing this catch turns
     * one of those into a 500 out of `POST /api/runs/trigger` instead of a clean refusal. `runCatching`
     * over a plain non-suspend call has no `CancellationException` to swallow.
     */
    private fun parse(text: String, sha: String?, origin: Origin, failurePrefix: String): Resolution =
        runCatching { PipelineDescriptor.parse(text) }.fold(
            onSuccess = { Resolved(it, sha, origin, text) },
            onFailure = { Rejected("$failurePrefix: ${it.message}") },
        )
}
