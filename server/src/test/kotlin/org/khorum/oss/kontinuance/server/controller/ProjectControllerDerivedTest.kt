package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.server.domain.RunApi
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Path
import java.time.Instant
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
    fun `reports the window its run counts were computed over`(@TempDir dir: Path) = runTest {
        assertEquals(500, controller(dir, InMemoryRunStore()).list().runWindow)
    }

    @Test
    fun `clamps the reported window to what the runs endpoint can serve`(@TempDir dir: Path) = runTest {
        // A derive window wider than the runs endpoint's cap would let the picker advertise runs the
        // dashboard could never load, however many it asked for — the count and the list would disagree
        // with no way for the operator to reconcile them.
        val descriptor = dir.resolve("kontinuance.yml")
        descriptor.writeText(descriptorText)
        val subject = ProjectController(
            ProjectStore(dir.resolve("projects")),
            InMemoryRunStore(),
            descriptor.toString(),
            RunApi.MAX_LIMIT * 10,
        )

        assertEquals(RunApi.MAX_LIMIT, subject.list().runWindow)
    }

    @Test
    fun `derives only within the clamped window`(@TempDir dir: Path) = runTest {
        // The reported window must be the one actually used, or it is just a decorative number.
        val runs = InMemoryRunStore()
        repeat(3) { i ->
            runs.record(RunRecord(id = "r$i", pipeline = "p", status = "Success", repo = "khorum-oss/demo"))
        }
        val descriptor = dir.resolve("kontinuance.yml")
        descriptor.writeText(descriptorText)
        val subject = ProjectController(ProjectStore(dir.resolve("projects")), runs, descriptor.toString(), 2)

        val listed = subject.list()

        assertEquals(2, listed.runWindow)
        assertEquals(2, listed.projects.single { it.name == "demo" }.runCount)
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
    fun `aggregates stats across multiple runs for the same project, newest wins`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        // Older run recorded FIRST: InMemoryRunStore.recent() returns newest-first as reverse insertion
        // order, so recording older-then-newer makes the newer run the first one deriveStats() sees for
        // this project — exactly the ordering deriveStats()'s "first seen == latest" comment relies on.
        // Statuses are deliberately different so an oldest-vs-newest bug is unambiguous.
        runs.record(
            RunRecord(
                id = "r1",
                pipeline = "relikquary-pr",
                status = "Failed",
                repo = "khorum-oss/relikquary",
                endedAt = Instant.parse("2026-08-01T00:00:00Z"),
            ),
        )
        runs.record(
            RunRecord(
                id = "r2",
                pipeline = "relikquary-pr",
                status = "Success",
                repo = "khorum-oss/relikquary",
                endedAt = Instant.parse("2026-08-02T00:00:00Z"),
            ),
        )

        val listed = controller(dir, runs).list().projects.single { it.name == "relikquary" }

        assertEquals(2, listed.runCount)
        assertEquals("Success", listed.lastStatus)
        assertEquals("2026-08-02T00:00:00Z", listed.lastRunAt)
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

    @Test
    fun `activating a derived project as the very first call still seeds default`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "relikquary-pr", status = "Success", repo = "khorum-oss/relikquary"))
        // Genuinely fresh state: no `.active` file, empty projects dir — controller() only writes the
        // live descriptor to disk, it never touches the projects dir or the active pointer.
        val subject = controller(dir, runs)

        // The FIRST call against this controller is activate(), not list() — exactly the direct-API-call
        // path the widened seedIfEmpty guard could permanently starve of seeding if activate() didn't
        // seed for itself.
        val response = subject.activate("relikquary")
        assertEquals(200, response.statusCode.value())

        val listed = subject.list()
        assertEquals("relikquary", listed.active)
        val default = listed.projects.singleOrNull { it.name == "default" }
        assertTrue(default != null, "default should have been seeded from the on-disk descriptor")
        assertTrue(default?.derived == false, "default should be registered, not merely derived")
    }
}
