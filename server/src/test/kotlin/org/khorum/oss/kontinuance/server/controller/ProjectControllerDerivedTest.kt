package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectControllerDerivedTest {

    private val descriptorText = """
        pipeline:
          name: "demo"
          stages: []
    """.trimIndent()

    private fun controller(dir: Path, runs: InMemoryRunStore): ProjectController {
        val descriptor = dir.resolve("kontinuance.yml")
        descriptor.writeText(descriptorText)
        return ProjectController(ProjectStore(dir.resolve("projects")), runs, descriptor.toString(), 500)
    }

    @Test
    fun `derives a project from runs that was never registered`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "relikquary-pr", status = "Success", repo = "khorum-oss/relikquary"))

        val listed = controller(dir, runs).list().projects.single { it.name == "relikquary" }

        assertTrue(listed.derived)
        assertTrue(!listed.runnable)
        assertEquals(1, listed.runCount)
        assertEquals("Success", listed.lastStatus)
    }

    @Test
    fun `a registered project wins over a derived one of the same name`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "relikquary-pr", status = "Success", project = "relikquary"))
        val subject = controller(dir, runs)
        subject.create(org.khorum.oss.kontinuance.server.domain.project.CreateProjectRequest("relikquary", descriptorText))

        val listed = subject.list().projects.single { it.name == "relikquary" }

        assertTrue(!listed.derived)
        assertTrue(listed.runnable)
        assertEquals(1, listed.runCount)
    }

    @Test
    fun `does not write derived projects to the store`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "relikquary-pr", status = "Success", repo = "khorum-oss/relikquary"))
        val store = ProjectStore(dir.resolve("projects"))
        val descriptor = dir.resolve("kontinuance.yml")
        descriptor.writeText(descriptorText)

        ProjectController(store, runs, descriptor.toString(), 500).list()

        assertTrue(!store.exists("relikquary"))
    }

    @Test
    fun `ignores runs that resolve to no project`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "local", status = "Success"))

        val names = controller(dir, runs).list().projects.map { it.name }

        assertEquals(listOf("default"), names)
    }

    @Test
    fun `activating a derived project does not overwrite the live descriptor`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "relikquary-pr", status = "Success", repo = "khorum-oss/relikquary"))
        val descriptor = dir.resolve("kontinuance.yml")
        descriptor.writeText(descriptorText)
        val subject = ProjectController(ProjectStore(dir.resolve("projects")), runs, descriptor.toString(), 500)

        val response = subject.activate("relikquary")

        assertEquals(200, response.statusCode.value())
        assertEquals(descriptorText, descriptor.readText())
        assertEquals("relikquary", subject.list().active)
    }

    @Test
    fun `activating an unknown project is still rejected`(@TempDir dir: Path) = runTest {
        val subject = controller(dir, InMemoryRunStore())

        assertEquals(404, subject.activate("nope").statusCode.value())
    }

    @Test
    fun `an active name matching no known project reads back as null`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        val subject = controller(dir, runs)
        // Register a real project first so seedIfEmpty (which would otherwise claim the empty store and
        // stamp its own "default" active pointer, masking what this test checks) has nothing to do.
        subject.create(org.khorum.oss.kontinuance.server.domain.project.CreateProjectRequest("relikquary", descriptorText))
        // Force a stale `.active` pointer directly in the store (bypassing activate's validation), then
        // list — the listing must guard against a pointer that no longer names a registered or derived
        // project rather than surfacing it (or blowing up) as the active project.
        ProjectStore(dir.resolve("projects")).setActive("ghost")

        assertEquals(null, subject.list().active)
    }
}
