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
}
