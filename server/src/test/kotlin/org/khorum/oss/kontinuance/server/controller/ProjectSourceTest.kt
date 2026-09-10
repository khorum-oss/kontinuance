package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.server.domain.project.GitHubClientProvider
import org.khorum.oss.kontinuance.server.domain.project.ProjectSource
import org.khorum.oss.kontinuance.server.domain.project.SourceRequest
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `POST /api/projects/{name}/source` — setting, changing, and clearing a project's source (033/041). */
class ProjectSourceTest {

    private val descriptorText = """
        pipeline:
          name: "demo"
          stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]
    """.trimIndent()

    private fun controller(dir: Path, store: ProjectStore) = ProjectController(
        store,
        InMemoryRunStore(),
        dir.resolve("live.yml").toString(),
        500,
        GitHubClientProvider { null },
        "kontinuance.yml",
    )

    @Test
    fun `clearing the source of a repo-only project is refused, not silently deregistering it`(
        @TempDir dir: Path,
    ) = runTest {
        // Since 041 the sidecar is the ONLY registration file of a project connected with just a repo,
        // and the entry screen offers EDIT SOURCE on exactly those projects. Saving a blank repo used to
        // delete that file: 200, the project gone from /api/projects, and — if it was active — a dangling
        // `.active` pointer that made the Config screen show the server's live descriptor instead.
        val store = ProjectStore(dir.resolve("projects"))
        store.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))

        val response = controller(dir, store).setSource("spektr", SourceRequest(repo = "", branch = ""))

        assertEquals(409, response.statusCode.value())
        assertTrue(store.exists("spektr"), "the project must survive a refused clear")
        assertTrue(store.source("spektr") != null, "its source must be untouched")
    }

    @Test
    fun `clearing the source of a project that has a stored descriptor still works`(@TempDir dir: Path) = runTest {
        // The pre-041 shape: a descriptor keeps the project registered, so dropping the source merely
        // returns it to running its descriptor as written.
        val store = ProjectStore(dir.resolve("projects"))
        store.save("spektr", descriptorText)
        store.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))

        val response = controller(dir, store).setSource("spektr", SourceRequest(repo = "", branch = ""))

        assertEquals(200, response.statusCode.value())
        assertEquals(null, store.source("spektr"))
        assertTrue(store.exists("spektr"))
    }

    @Test
    fun `setting a source on a repo-only project still works`(@TempDir dir: Path) = runTest {
        val store = ProjectStore(dir.resolve("projects"))
        store.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))

        val response = controller(dir, store)
            .setSource("spektr", SourceRequest(repo = "https://github.com/khorum-oss/spektr", branch = "release"))

        assertEquals(200, response.statusCode.value())
        assertEquals("release", store.source("spektr")?.branch)
    }
}
