package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.client.GitHubClient
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.support.RecordingGitHubClient
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.server.domain.project.CreateProjectRequest
import org.khorum.oss.kontinuance.server.domain.project.CreatedProject
import org.khorum.oss.kontinuance.server.domain.project.GitHubClientProvider
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.io.IOException
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

    @Test
    fun `creates the project anyway when the repo-hosted descriptor fails to parse, with a warning`(
        @TempDir dir: Path,
    ) = runTest {
        // fileAt resolves to something, but it's not a valid descriptor — this is the DescriptorException
        // branch of checkRepository, distinct from the file-not-found branch above.
        val controller = controllerFor(
            dir,
            branchHeads = mapOf("main" to "abc"),
            files = mapOf("kontinuance.yml" to "not: [valid"),
        )

        val response = controller.create(
            CreateProjectRequest(name = "spektr", repo = "https://github.com/khorum-oss/spektr", branch = "main"),
        )

        assertEquals(200, response.statusCode.value())
        val body = response.body as CreatedProject
        assertEquals(false, body.descriptor?.ok)
        val message = body.descriptor?.message!!
        assertTrue(message.contains("khorum-oss/spektr"), message)
        assertTrue(message.contains("main"), message)
        assertTrue(message.contains("kontinuance.yml"), message)
        assertTrue(ProjectStore(dir.resolve("projects")).source("spektr") != null)
    }

    @Test
    fun `creates the project anyway when GitHub rejects the request, with a warning naming the status`(
        @TempDir dir: Path,
    ) = runTest {
        val failing = object : GitHubClient by RecordingGitHubClient() {
            override suspend fun branchHead(repo: RepoRef, branch: String): String? =
                throw GitHubApiException(500, "boom")
        }
        val controller = controllerWithClient(dir, failing)

        val response = controller.create(
            CreateProjectRequest(name = "spektr", repo = "https://github.com/khorum-oss/spektr", branch = "main"),
        )

        assertEquals(200, response.statusCode.value())
        val body = response.body as CreatedProject
        assertEquals(false, body.descriptor?.ok)
        val message = body.descriptor?.message!!
        assertTrue(message.contains("khorum-oss/spektr"), message)
        assertTrue(message.contains("500"), message)
        assertTrue(ProjectStore(dir.resolve("projects")).source("spektr") != null)
    }

    @Test
    fun `creates the project anyway when GitHub is unreachable, with a warning`(@TempDir dir: Path) = runTest {
        // No HTTP response at all — DNS failure, connection refused, TLS failure, timeout — surfaces as
        // an IOException, not a GitHubApiException (which only exists once GitHub answered).
        val unreachable = object : GitHubClient by RecordingGitHubClient() {
            override suspend fun branchHead(repo: RepoRef, branch: String): String? =
                throw IOException("connection refused")
        }
        val controller = controllerWithClient(dir, unreachable)

        val response = controller.create(
            CreateProjectRequest(name = "spektr", repo = "https://github.com/khorum-oss/spektr", branch = "main"),
        )

        assertEquals(200, response.statusCode.value())
        val body = response.body as CreatedProject
        assertEquals(false, body.descriptor?.ok)
        assertTrue(body.descriptor?.message!!.contains("unreachable"))
        assertTrue(ProjectStore(dir.resolve("projects")).source("spektr") != null)
    }

    @Test
    fun `creates the project anyway when no GitHub token is available, with a warning`(@TempDir dir: Path) = runTest {
        val controller = ProjectController(
            store = ProjectStore(dir.resolve("projects")),
            runs = InMemoryRunStore(),
            descriptorPath = dir.resolve("live.yml").toString(),
            deriveLimit = 500,
            clients = GitHubClientProvider { null },
            descriptorFileName = "kontinuance.yml",
        )

        val response = controller.create(
            CreateProjectRequest(name = "spektr", repo = "https://github.com/khorum-oss/spektr", branch = "main"),
        )

        assertEquals(200, response.statusCode.value())
        val body = response.body as CreatedProject
        assertEquals(false, body.descriptor?.ok)
        assertTrue(body.descriptor?.message!!.contains("token"))
        assertTrue(ProjectStore(dir.resolve("projects")).source("spektr") != null)
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
    ): ProjectController = controllerWithClient(dir, RecordingGitHubClient(branchHeads = branchHeads, files = files))

    private fun controllerWithClient(dir: Path, client: GitHubClient): ProjectController = ProjectController(
        store = ProjectStore(dir.resolve("projects")),
        runs = InMemoryRunStore(),
        descriptorPath = dir.resolve("live.yml").toString(),
        deriveLimit = 500,
        clients = GitHubClientProvider { client },
        descriptorFileName = "kontinuance.yml",
    )
}
