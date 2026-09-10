package org.khorum.oss.kontinuance.server.domain.project

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.github.client.CommitStatus
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.client.GitHubClient
import org.khorum.oss.kontinuance.github.client.PullRequest
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.support.RecordingGitHubClient
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class DescriptorResolverTest {

    private val descriptorText = """
        pipeline:
          name: "demo"
          stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]
    """.trimIndent()

    /**
     * Fails the test immediately if any method is invoked, so a test can assert "never touches GitHub"
     * as a real behavioural claim rather than relying on [RecordingGitHubClient]'s default empty answers,
     * which would let a resolver that calls GitHub by mistake pass unnoticed.
     */
    private class FailingGitHubClient : GitHubClient {
        override suspend fun listOpenPullRequests(repo: RepoRef): List<PullRequest> =
            fail("GitHub should not have been called")

        override suspend fun branchHead(repo: RepoRef, branch: String): String? =
            fail("GitHub should not have been called")

        override suspend fun fileAt(repo: RepoRef, path: String, ref: String): String? =
            fail("GitHub should not have been called")

        override suspend fun createCommitStatus(repo: RepoRef, sha: String, status: CommitStatus): Unit =
            fail("GitHub should not have been called")
    }

    private fun resolverFor(
        dir: Path,
        client: GitHubClient? = null,
        live: Path = dir.resolve("live.yml"),
    ) = DescriptorResolver(
        projects = ProjectStore(dir.resolve("projects")),
        liveDescriptor = live,
        descriptorPath = "kontinuance.yml",
        clients = GitHubClientProvider { client },
    )

    @Test
    fun `a stored descriptor wins and never touches GitHub`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.save("spektr", descriptorText)
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        // A client that fails the test if called at all.
        val client = FailingGitHubClient()

        val result = resolverFor(dir, client).resolve()

        val resolved = assertIs<Resolved>(result)
        assertEquals("demo", resolved.pipeline.name)
        assertEquals(Origin.Stored, resolved.origin)
        assertEquals(null, resolved.sha)
    }

    @Test
    fun `a repo-hosted project resolves branch to sha and fetches the descriptor there`(
        @TempDir dir: Path,
    ) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(
            branchHeads = mapOf("main" to "abc123"),
            files = mapOf("kontinuance.yml" to descriptorText),
        )

        val result = resolverFor(dir, client).resolve()

        val resolved = assertIs<Resolved>(result)
        assertEquals("demo", resolved.pipeline.name)
        assertEquals(Origin.Repo, resolved.origin)
        assertEquals("abc123", resolved.sha)
    }

    @Test
    fun `falls back to the live descriptor when no project is active`(@TempDir dir: Path) = runTest {
        val live = dir.resolve("live.yml")
        live.writeText(descriptorText)

        val result = resolverFor(dir, client = null, live = live).resolve()

        val resolved = assertIs<Resolved>(result)
        assertEquals(Origin.Live, resolved.origin)
    }

    @Test
    fun `rejects a source that is not a GitHub repository`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://gitlab.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")

        val result = resolverFor(dir, RecordingGitHubClient()).resolve()

        // Not "GitHub" — that substring also appears in several other rejection messages (e.g. "no
        // GitHub token available"), so asserting on it would pass even if the wrong branch rejected.
        assertTrue(assertIs<Rejected>(result).reason.contains("gitlab.com"), result.toString())
    }

    @Test
    fun `rejects when no token is available`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")

        val result = resolverFor(dir, client = null).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("token"), result.toString())
    }

    @Test
    fun `rejects an unknown branch`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "nope"))
        projects.setActive("spektr")

        val result = resolverFor(dir, RecordingGitHubClient()).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("nope"), result.toString())
    }

    @Test
    fun `rejects when the repository has no descriptor at that path`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(branchHeads = mapOf("main" to "abc123"))

        val result = resolverFor(dir, client).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("kontinuance.yml"), result.toString())
    }

    @Test
    fun `rejects a descriptor that does not parse, naming its origin`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(
            branchHeads = mapOf("main" to "abc123"),
            // Valid YAML, invalid descriptor: no stages (041 depends on the 040-era guard).
            files = mapOf("kontinuance.yml" to "pipeline:\n  name: \"broken\"\n"),
        )

        val result = resolverFor(dir, client).resolve()

        val reason = assertIs<Rejected>(result).reason
        // Naming the origin is the whole point: an operator needs to know it was the repo-hosted
        // descriptor at khorum-oss/spektr@main:kontinuance.yml that was stage-less, not "something,
        // somewhere". A bare contains("stage") can't tell the two apart — the engine's own
        // DescriptorException message contains that substring regardless of where the text came from.
        assertTrue(reason.contains("khorum-oss/spektr@main:kontinuance.yml"), reason)
        assertTrue(reason.contains("stage"), reason)
    }

    @Test
    fun `rejects a descriptor whose parse failure is not a DescriptorException — an empty secret`(
        @TempDir dir: Path,
    ) = runTest {
        // `SecretRef(asString(...))` is evaluated outside the parser's `construct { }` wrapper, so an
        // empty secret name raises a bare IllegalArgumentException rather than a DescriptorException.
        // Resolution must still reject: RunTrigger refuses before a run record exists only if nothing
        // thrown by the parser can escape resolve().
        val result = resolveRepoDescriptor(
            dir,
            """
                pipeline:
                  name: "demo"
                  stages: [{ name: "s", steps: [{ name: "x", run: "true", secrets: [""] }] }]
            """.trimIndent(),
        )

        val reason = assertIs<Rejected>(result).reason
        assertTrue(reason.contains("khorum-oss/spektr@main:kontinuance.yml"), reason)
    }

    @Test
    fun `rejects a descriptor whose parse failure is not a DescriptorException — an oversized timeout`(
        @TempDir dir: Path,
    ) = runTest {
        // The duration regex matches any run of digits, then `toLong()` overflows with a
        // NumberFormatException — again not a DescriptorException.
        val result = resolveRepoDescriptor(
            dir,
            """
                pipeline:
                  name: "demo"
                  stages:
                    - name: "s"
                      steps: [{ name: "x", run: "true", timeout: "99999999999999999999s" }]
            """.trimIndent(),
        )

        val reason = assertIs<Rejected>(result).reason
        assertTrue(reason.contains("khorum-oss/spektr@main:kontinuance.yml"), reason)
    }

    /** An active repo-hosted project whose repository serves [descriptor], resolved. */
    private suspend fun resolveRepoDescriptor(dir: Path, descriptor: String): Resolution {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(
            branchHeads = mapOf("main" to "abc123"),
            files = mapOf("kontinuance.yml" to descriptor),
        )
        return resolverFor(dir, client).resolve()
    }

    @Test
    fun `rejects rather than raising when the API fails`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val failing = object : GitHubClient by RecordingGitHubClient() {
            override suspend fun branchHead(
                repo: RepoRef,
                branch: String,
            ): String? = throw GitHubApiException(500, "boom")
        }

        val result = resolverFor(dir, failing).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("500"), result.toString())
    }

    @Test
    fun `rejects rather than raising when GitHub is unreachable`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        // No HTTP response at all — DNS failure, connection refused, TLS failure, timeout — surfaces as
        // an IOException, not a GitHubApiException (which only exists once GitHub answered). This is the
        // exact case FR-005 names as "an unreachable API"; it must not escape resolve() as a thrown
        // exception.
        val unreachable = object : GitHubClient by RecordingGitHubClient() {
            override suspend fun branchHead(repo: RepoRef, branch: String): String? =
                throw IOException("connection refused")
        }

        val result = resolverFor(dir, unreachable).resolve()

        val reason = assertIs<Rejected>(result).reason
        assertTrue(reason.contains("unreachable"), reason)
        assertTrue(reason.contains("connection refused"), reason)
    }

    @Test
    fun `rejects rather than raising when the branch cannot be put in a URL`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        // A branch the operator typed. The client percent-encodes it now, but this resolver is handed
        // whatever GitHubClient implementation the server was wired with — belt and braces, so a URL
        // that still cannot be built is a rejection, not a 500 out of POST /api/runs/trigger.
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "100%done"))
        projects.setActive("spektr")
        val unencoded = object : GitHubClient by RecordingGitHubClient() {
            override suspend fun branchHead(repo: RepoRef, branch: String): String? =
                throw IllegalArgumentException("Illegal character in path")
        }

        val result = resolverFor(dir, unencoded).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("100%done"), result.toString())
    }

    @Test
    fun `rejects when there is no active project and no live descriptor`(@TempDir dir: Path) = runTest {
        // Fourth FR-009a outcome: no active project, and the live-descriptor fallback also has nothing.
        val result = resolverFor(dir, client = null, live = dir.resolve("no-such-file.yml")).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("no pipeline descriptor"), result.toString())
    }

    @Test
    fun `falls back to the live descriptor when the active project has no stored descriptor or source`(
        @TempDir dir: Path,
    ) = runTest {
        // The precedence subtlety most likely to regress: an active project with neither a stored
        // descriptor nor a source must fall through to the live file, not reject outright.
        val projects = ProjectStore(dir.resolve("projects"))
        projects.setActive("spektr")
        val live = dir.resolve("live.yml")
        live.writeText(descriptorText)

        val result = resolverFor(dir, client = null, live = live).resolve()

        val resolved = assertIs<Resolved>(result)
        assertEquals(Origin.Live, resolved.origin)
    }

    @Test
    fun `rejects a source with a repository but no branch`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        // No branch saved at all — ProjectStore.source() still reports hasRepo == true, so this path is
        // reachable, not the same as "no source".
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", branch = null))
        projects.setActive("spektr")
        // A client that would fail the test if reached — the branch check must short-circuit before it.
        val client = FailingGitHubClient()

        val result = resolverFor(dir, client).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("branch"), result.toString())
    }
}
