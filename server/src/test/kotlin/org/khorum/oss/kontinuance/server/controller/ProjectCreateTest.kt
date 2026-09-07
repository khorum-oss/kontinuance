package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.github.support.RecordingGitHubClient
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.server.domain.project.CreateProjectRequest
import org.khorum.oss.kontinuance.server.domain.project.CreatedProject
import org.khorum.oss.kontinuance.server.domain.project.GitHubClientProvider
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectCreateTest {

    @Test
    fun `creates a project with a repo and no descriptor text`(@TempDir dir: Path) = runTest {
        val controller = controllerFor(dir, branchHeads = mapOf("main" to "abc"), files = mapOf("kontinuance.yml" to valid))

        val response = controller.create(
            CreateProjectRequest(name = "spektr", repo = "https://github.com/khorum-oss/spektr", branch = "main"),
        )

        assertEquals(200, response.statusCode.value())
        val body = response.body as CreatedProject
        assertEquals(true, body.descriptor?.ok)
        assertEquals("demo", body.descriptor?.pipeline)
        assertEquals(1, body.descriptor?.stages)
    }

    @Test
    fun `creates the project anyway when its descriptor cannot be found, with a warning`(
        @TempDir dir: Path,
    ) = runTest {
        val controller = controllerFor(dir, branchHeads = mapOf("main" to "abc"))

        val response = controller.create(
            CreateProjectRequest(name = "spektr", repo = "https://github.com/khorum-oss/spektr", branch = "main"),
        )

        assertEquals(200, response.statusCode.value())
        val body = response.body as CreatedProject
        assertEquals(false, body.descriptor?.ok)
        assertTrue(body.descriptor?.message!!.contains("kontinuance.yml"))
    }

    @Test
    fun `still rejects a project with neither a descriptor nor a repo`(@TempDir dir: Path) = runTest {
        val response = controllerFor(dir).create(CreateProjectRequest(name = "spektr"))

        assertEquals(400, response.statusCode.value())
    }

    private val valid = """
        pipeline:
          name: "demo"
          stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]
    """.trimIndent()

    private fun controllerFor(
        dir: Path,
        branchHeads: Map<String, String> = emptyMap(),
        files: Map<String, String> = emptyMap(),
    ): ProjectController = ProjectController(
        store = ProjectStore(dir.resolve("projects")),
        runs = InMemoryRunStore(),
        descriptorPath = dir.resolve("live.yml").toString(),
        deriveLimit = 500,
        clients = GitHubClientProvider { RecordingGitHubClient(branchHeads = branchHeads, files = files) },
        descriptorFileName = "kontinuance.yml",
    )
}
