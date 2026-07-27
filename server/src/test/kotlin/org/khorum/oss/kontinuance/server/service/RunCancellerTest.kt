package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.execution.StatusEvent
import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.engine.secret.SecretSource
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit checks for [RunCanceller]'s status gating and id routing (028): a running run is cancelled through
 * the engine by its own id; a terminal or gated run is "not active"; an unknown id is "not found". The
 * engine's actual cancel-by-id behavior is covered by the engine `CancellationTest`.
 */
class RunCancellerTest {

    /** A [PipelineEngine] that only records which run ids were asked to cancel. */
    private class RecordingEngine : PipelineEngine {
        val canceled = mutableListOf<String>()

        override suspend fun run(
            pipeline: Pipeline,
            secrets: SecretSource,
            completedStages: List<StageRun>,
            logSink: LogSink?,
            runId: RunId?,
        ): Run = error("run() is not exercised by cancel tests")

        override fun statuses(runId: RunId): Flow<StatusEvent> = emptyFlow()

        override suspend fun cancel(runId: RunId) {
            canceled.add(runId.value)
        }
    }

    private fun canceller(vararg records: RunRecord): Pair<RunCanceller, RecordingEngine> {
        val store = InMemoryRunStore().apply { records.forEach { record(it) } }
        val engine = RecordingEngine()
        return RunCanceller(store, engine) to engine
    }

    @Test
    fun `a running run is cancelled through the engine by its id`() = runBlocking {
        val (canceller, engine) = canceller(RunRecord(id = "r", pipeline = "p", status = "Running"))

        assertEquals(RunCanceller.Outcome.CANCELLING, canceller.cancel("r"))
        assertEquals(listOf("r"), engine.canceled)
    }

    @Test
    fun `a terminal run is not active and is not cancelled`() = runBlocking {
        val (canceller, engine) = canceller(RunRecord(id = "r", pipeline = "p", status = "Success"))

        assertEquals(RunCanceller.Outcome.NOT_ACTIVE, canceller.cancel("r"))
        assertTrue(engine.canceled.isEmpty())
    }

    @Test
    fun `a run waiting at a gate is not cancellable here (reject ends it instead)`() = runBlocking {
        val (canceller, engine) = canceller(RunRecord(id = "r", pipeline = "p", status = "WaitingOnApproval"))

        assertEquals(RunCanceller.Outcome.NOT_ACTIVE, canceller.cancel("r"))
        assertTrue(engine.canceled.isEmpty())
    }

    @Test
    fun `an unknown run is not found`() = runBlocking {
        val (canceller, engine) = canceller()

        assertEquals(RunCanceller.Outcome.NOT_FOUND, canceller.cancel("nope"))
        assertTrue(engine.canceled.isEmpty())
    }
}