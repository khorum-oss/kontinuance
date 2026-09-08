package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.execution.StatusEvent
import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.engine.secret.SecretSource
import org.khorum.oss.kontinuance.persistence.InMemoryRunLogStore
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit-tests [RunTrigger] with an in-memory store and a fake engine, using an [kotlinx.coroutines.Dispatchers.Unconfined]
 * scope so the background run executes inline (making the terminal record observable without waiting).
 */
class RunTriggerTest {

    /** A fake engine whose `run` returns [outcome] (or throws [failWith]) without shelling out. */
    private class FakeEngine(
        private val outcome: PipelineStatus = PipelineStatus.Success,
        private val failWith: Throwable? = null,
    ) : PipelineEngine {
        override suspend fun run(
            pipeline: Pipeline,
            secrets: SecretSource,
            completedStages: List<StageRun>,
            logSink: LogSink?,
            runId: RunId?,
        ): Run {
            failWith?.let { throw it }
            logSink?.emit("[demo] hello")
            return Run(runId ?: RunId("engine-generated"), pipeline, outcome, emptyList())
        }

        override fun statuses(runId: RunId): Flow<StatusEvent> = throw UnsupportedOperationException()
        override suspend fun cancel(runId: RunId): Unit = throw UnsupportedOperationException()
    }

    private val validDescriptor = """
        pipeline:
          name: "demo"
          stages: []
    """.trimIndent()

    /**
     * An [InMemoryRunStore] that also keeps every write in order. The store overwrites by id, so without
     * this the initial `Running` record is gone by the time the inline background run has replaced it —
     * and that record is exactly what the runs list shows while a run is in flight.
     */
    private class RecordingRunStore : RunStore {
        private val delegate = InMemoryRunStore()
        val writes = mutableListOf<RunRecord>()

        override fun record(record: RunRecord) {
            writes += record
            delegate.record(record)
        }

        override fun recent(limit: Int): List<RunRecord> = delegate.recent(limit)
        override fun get(id: String): RunRecord? = delegate.get(id)
    }

    private val stagedDescriptor = """
        pipeline:
          name: "demo"
          stages:
            - name: "build"
              steps:
                - name: "assemble"
                  run: "true"
    """.trimIndent()

    private fun triggerFor(store: RunStore, engine: PipelineEngine, path: Path): RunTrigger {
        val launcher = RunLauncher(store, engine, CoroutineScope(Dispatchers.Unconfined), InMemoryRunLogStore())
        val projects = ProjectStore(path.resolveSibling("projects"))
        return RunTrigger(store, launcher, projects, path.toString())
    }

    /** Registers [name] as a project and makes it active, as the entry picker does. */
    private fun activate(path: Path, name: String, text: String): ProjectStore =
        ProjectStore(path.resolveSibling("projects")).apply {
            save(name, text)
            setActive(name)
        }

    @Test
    fun `rejects when no descriptor file is present`(@TempDir dir: Path) {
        val result = triggerFor(InMemoryRunStore(), FakeEngine(), dir.resolve("missing.yml")).trigger()
        assertTrue(result is RunTrigger.Result.Rejected, "expected rejection when descriptor is absent")
    }

    @Test
    fun `rejects an invalid descriptor without recording a run`(@TempDir dir: Path) {
        val store = InMemoryRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, "pipeline:\n  bogusKey: 1\n")

        val result = triggerFor(store, FakeEngine(), file).trigger()

        assertTrue(result is RunTrigger.Result.Rejected)
        assertTrue(store.recent(10).isEmpty(), "no run should be recorded for an invalid descriptor")
    }

    @Test
    fun `accepts a valid descriptor and records the terminal run under the returned id`(@TempDir dir: Path) {
        val store = InMemoryRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, validDescriptor)

        val result = triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        assertTrue(result is RunTrigger.Result.Accepted)
        val id = result.id
        assertTrue(id.startsWith("run-"), "id should be a generated run id, was $id")

        // Unconfined scope ran the background block inline, so the terminal record is already stored.
        val record = store.get(id)
        assertNotNull(record, "the run should be recorded under the accepted id")
        assertEquals("demo", record.pipeline)
        assertEquals("Success", record.status)
        assertEquals("manual", record.trigger)
    }

    @Test
    fun `records a Failed run when the engine throws`(@TempDir dir: Path) {
        val store = InMemoryRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, validDescriptor)

        val result = triggerFor(store, FakeEngine(failWith = IllegalStateException("boom")), file).trigger()

        assertTrue(result is RunTrigger.Result.Accepted)
        val id = result.id
        val record = assertNotNull(store.get(id))
        assertEquals("Failed", record.status)
        assertEquals("boom", record.reason)
    }

    @Test
    fun `stamps the active project on every record when the descriptor declares none`(@TempDir dir: Path) {
        val store = RecordingRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, validDescriptor)
        activate(file, "web-ui", validDescriptor)

        val result = triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        assertTrue(result is RunTrigger.Result.Accepted)
        // Both writes — the immediate `Running` one the runs list shows while the run is in flight, and the
        // terminal one — must name the project, or the run vanishes from the project-scoped list (039).
        assertEquals(2, store.writes.size, "expected a running record and a terminal record")
        assertTrue(store.writes.all { it.project == "web-ui" }, "every record should name the launching project")
    }

    @Test
    fun `the descriptor's own project wins over the project it was launched under`(@TempDir dir: Path) {
        val store = RecordingRunStore()
        val declaring = validDescriptor.replace("name: \"demo\"", "name: \"demo\"\n  project: \"platform\"")
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, declaring)
        activate(file, "web-ui", declaring)

        triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        assertTrue(store.writes.all { it.project == "platform" }, "the descriptor's `project:` key takes precedence")
    }

    @Test
    fun `leaves the project unset when nothing is active and the descriptor declares none`(@TempDir dir: Path) {
        val store = RecordingRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, validDescriptor)

        triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        // No invented owner: the reader still falls back to the repo's short name (039).
        assertTrue(store.writes.all { it.project == null })
    }

    @Test
    fun `records the declared stage breakdown before the run produces any result`(@TempDir dir: Path) {
        val store = RecordingRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, stagedDescriptor)

        triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        val running = store.writes.first()
        assertEquals("Running", running.status)
        val stage = running.stages.single()
        assertEquals("build", stage.name)
        assertEquals("Pending", stage.status)
        val step = stage.steps.single()
        assertEquals("assemble", step.name)
        assertEquals("Pending", step.status)
        assertNull(step.startedAt, "a step that has not started carries no start time")
    }
}
