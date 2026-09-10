package org.khorum.oss.kontinuance.server.controller.config

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.github.support.RecordingGitHubClient
import org.khorum.oss.kontinuance.server.domain.ConfigUpdateRequest
import org.khorum.oss.kontinuance.server.domain.project.DescriptorResolver
import org.khorum.oss.kontinuance.server.domain.project.GitHubClientProvider
import org.khorum.oss.kontinuance.server.domain.project.ProjectSource
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `/api/config` reporting where the displayed descriptor came from, and reverting a stored override back
 * to the project's repository (041, FR-008).
 */
class ConfigOriginTest {

    private val valid = """
        pipeline:
          name: "demo"
          stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]
    """.trimIndent()

    private fun controllerFor(dir: Path, repoHosted: Boolean): ConfigController {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        if (!repoHosted) projects.save("spektr", valid)
        projects.setActive("spektr")
        val clients = GitHubClientProvider {
            RecordingGitHubClient(
                branchHeads = mapOf("main" to "abc123"),
                files = mapOf("kontinuance.yml" to valid),
            )
        }
        val resolver = DescriptorResolver(
            projects = projects,
            liveDescriptor = dir.resolve("live.yml"),
            descriptorPath = "kontinuance.yml",
            clients = clients,
        )
        return ConfigController(
            descriptorPath = dir.resolve("live.yml").toString(),
            projects = projects,
            resolver = resolver,
        )
    }

    @Test
    fun `reports a repo-hosted descriptor as not overridden`(@TempDir dir: Path) = runTest {
        val response = controllerFor(dir, repoHosted = true).config()

        assertEquals("repo", response.origin)
        assertFalse(response.overridden)
    }

    @Test
    fun `saving an edit for a repo-hosted project creates a visible override`(@TempDir dir: Path) = runTest {
        val controller = controllerFor(dir, repoHosted = true)

        controller.update(ConfigUpdateRequest(text = valid))

        val after = controller.config()
        assertEquals("stored", after.origin)
        assertTrue(after.overridden)
    }

    @Test
    fun `reverting removes the override and returns to the repository's descriptor`(
        @TempDir dir: Path,
    ) = runTest {
        val controller = controllerFor(dir, repoHosted = true)
        controller.update(ConfigUpdateRequest(text = valid))

        val response = controller.revert()

        assertEquals(200, response.statusCode.value())
        assertEquals("repo", controller.config().origin)
    }

    @Test
    fun `reverting a project that is not overriding anything is a conflict`(@TempDir dir: Path) = runTest {
        val controller = controllerFor(dir, repoHosted = true)

        assertEquals(409, controller.revert().statusCode.value())
    }

    @Test
    fun `reverting deletes only the stored descriptor, leaving the project registered`(
        @TempDir dir: Path,
    ) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.save("spektr", valid)
        projects.setActive("spektr")
        val clients = GitHubClientProvider {
            RecordingGitHubClient(
                branchHeads = mapOf("main" to "abc123"),
                files = mapOf("kontinuance.yml" to valid),
            )
        }
        val resolver = DescriptorResolver(
            projects = projects,
            liveDescriptor = dir.resolve("live.yml"),
            descriptorPath = "kontinuance.yml",
            clients = clients,
        )
        val controller = ConfigController(
            descriptorPath = dir.resolve("live.yml").toString(),
            projects = projects,
            resolver = resolver,
        )

        controller.revert()

        assertTrue(projects.exists("spektr"), "the project should still be registered by its source sidecar")
        assertEquals(null, projects.get("spektr"), "the stored descriptor should be gone")
        assertTrue("spektr" in projects.list())
    }

    @Test
    fun `reports why resolution failed instead of showing an unrelated descriptor`(@TempDir dir: Path) = runTest {
        // A repo-hosted project whose branch was deleted (or token expired, or rate limit exhausted).
        // Falling back to the server's live descriptor file shows whichever project was activated last,
        // labelled as this project's — and one EDIT + SAVE later that unrelated pipeline is this
        // project's stored override. Meanwhile RunTrigger refuses with the reason, so the two surfaces
        // would be describing the same project differently.
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val staleLocal = """
            pipeline:
              name: "stale-local"
              stages: [{ name: "only-stage", steps: [{ name: "x", run: "true" }] }]
        """.trimIndent()
        dir.resolve("live.yml").writeText(staleLocal)
        // No branch heads: the branch is gone, so resolution rejects.
        val resolver = DescriptorResolver(
            projects = projects,
            liveDescriptor = dir.resolve("live.yml"),
            descriptorPath = "kontinuance.yml",
            clients = GitHubClientProvider { RecordingGitHubClient() },
        )
        val controller = ConfigController(
            descriptorPath = dir.resolve("live.yml").toString(),
            projects = projects,
            resolver = resolver,
        )

        val response = controller.config()

        assertEquals("unresolved", response.origin)
        assertTrue(response.reason?.contains("main") == true, response.reason.toString())
        assertFalse(response.text.contains("stale-local"), "must not show an unrelated descriptor")
        assertFalse(response.overridden)
    }

    @Test
    fun `a server with no active project still serves its live descriptor file`(@TempDir dir: Path) = runTest {
        // FR-009a: the plain single-descriptor deployment must be untouched by any of this.
        val projects = ProjectStore(dir.resolve("projects"))
        dir.resolve("live.yml").writeText(valid)
        val resolver = DescriptorResolver(
            projects = projects,
            liveDescriptor = dir.resolve("live.yml"),
            descriptorPath = "kontinuance.yml",
            clients = GitHubClientProvider { null },
        )
        val controller = ConfigController(
            descriptorPath = dir.resolve("live.yml").toString(),
            projects = projects,
            resolver = resolver,
        )

        val response = controller.config()

        assertEquals("live", response.origin)
        assertEquals(valid, response.text)
    }

    @Test
    fun `a fresh server with nothing to show falls back to fixture content, not an error`(
        @TempDir dir: Path,
    ) = runTest {
        // No active project and no descriptor on disk: the pre-041 behaviour of showing fixture content
        // is kept, because there is no project whose resolution could be said to have failed.
        val projects = ProjectStore(dir.resolve("projects"))
        val resolver = DescriptorResolver(
            projects = projects,
            liveDescriptor = dir.resolve("live.yml"),
            descriptorPath = "kontinuance.yml",
            clients = GitHubClientProvider { null },
        )
        val controller = ConfigController(
            descriptorPath = dir.resolve("live.yml").toString(),
            projects = projects,
            resolver = resolver,
        )

        val response = controller.config()

        assertEquals("live", response.origin)
        assertEquals(null, response.reason)
    }

    @Test
    fun `shows the repository's descriptor rather than a stale local file, for a repo-hosted project`(
        @TempDir dir: Path,
    ) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")

        // The live descriptor file holds a stale, unrelated pipeline — e.g. left over from whichever
        // project was activated before this one — clearly distinguishable from the repo's descriptor by
        // both name and stage count.
        val staleLocal = """
            pipeline:
              name: "stale-local"
              stages: [{ name: "only-stage", steps: [{ name: "x", run: "true" }] }]
        """.trimIndent()
        dir.resolve("live.yml").writeText(staleLocal)

        val fromRepo = """
            pipeline:
              name: "from-repo"
              stages:
                - name: "build"
                  steps: [{ name: "x", run: "true" }]
                - name: "test"
                  steps: [{ name: "y", run: "true" }]
        """.trimIndent()
        val clients = GitHubClientProvider {
            RecordingGitHubClient(
                branchHeads = mapOf("main" to "abc123"),
                files = mapOf("kontinuance.yml" to fromRepo),
            )
        }
        val resolver = DescriptorResolver(
            projects = projects,
            liveDescriptor = dir.resolve("live.yml"),
            descriptorPath = "kontinuance.yml",
            clients = clients,
        )
        val controller = ConfigController(
            descriptorPath = dir.resolve("live.yml").toString(),
            projects = projects,
            resolver = resolver,
        )

        val response = controller.config()

        assertEquals("repo", response.origin)
        assertEquals(fromRepo, response.text, "text must be the repository's descriptor, not the stale local file")
        assertEquals(2, response.plan.stages, "plan must be derived from the repository's descriptor too")
        assertEquals(2, response.plan.tasks)
    }
}
