package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Path
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
}
