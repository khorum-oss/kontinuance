package org.khorum.oss.kontinuance.server.trigger

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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit-tests [RunTrigger] with an in-memory store and a fake engine, using an [Dispatchers.Unconfined]
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
        ): Run {
            failWith?.let { throw it }
            logSink?.emit("[demo] hello")
            return Run(RunId("engine-generated"), pipeline, outcome, emptyList())
        }

        override fun statuses(runId: RunId): Flow<StatusEvent> = throw UnsupportedOperationException()
        override suspend fun cancel(runId: RunId): Unit = throw UnsupportedOperationException()
    }

    private val validDescriptor = """
        pipeline:
          name: "demo"
          stages: []
    """.trimIndent()

    private fun triggerFor(store: InMemoryRunStore, engine: PipelineEngine, path: Path): RunTrigger {
        val launcher = RunLauncher(store, engine, CoroutineScope(Dispatchers.Unconfined), InMemoryRunLogStore())
        return RunTrigger(store, launcher, path.toString())
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
}
