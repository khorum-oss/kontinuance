package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.dsl.steps.approvalStep
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApprovalGateTest {

    /** A one-stage pipeline whose only step is a manual approval gate, built via the DSL. */
    private fun gatedPipeline(): Pipeline = pipeline {
        name = "gated"
        stages {
            stage {
                name = "promote"
                steps { approvalStep("approve", message = "Promote to production?") }
            }
        }
    }

    @Test
    fun `the default auto-approving gate lets a gated pipeline run to Success`() = runBlocking {
        val completed = PipelineEngine.default(CapturingLogSink()).run(gatedPipeline())

        assertEquals(PipelineStatus.Success, completed.status)
        assertEquals(PipelineStatus.Success, completed.stageRuns[0].stepRuns[0].status)
    }

    @Test
    fun `a rejected gate ends the run Cancelled, not Failed`() = runBlocking {
        val engine = PipelineEngine.default(CapturingLogSink()) { _, _ -> ApprovalDecision.REJECTED }

        val completed = engine.run(gatedPipeline())

        assertEquals(PipelineStatus.Cancelled, completed.status)
        assertEquals(PipelineStatus.Cancelled, completed.stageRuns[0].stepRuns[0].status)
    }

    @Test
    fun `the gate receives the ApprovalToken run id and resumes on approval`() = runBlocking {
        val seenRunId = CompletableDeferred<String?>()
        val decide = CompletableDeferred<ApprovalDecision>()
        val engine = PipelineEngine.default(CapturingLogSink()) { runId, _ ->
            seenRunId.complete(runId)
            decide.await()
        }

        val run = async { withContext(ApprovalToken("run-42")) { engine.run(gatedPipeline()) } }

        assertEquals("run-42", seenRunId.await())
        decide.complete(ApprovalDecision.APPROVED)
        assertEquals(PipelineStatus.Success, run.await().status)
    }

    @Test
    fun `without an ApprovalToken the gate run id is null`() = runBlocking {
        val seenRunId = CompletableDeferred<String?>()
        val engine = PipelineEngine.default(CapturingLogSink()) { runId, _ ->
            seenRunId.complete(runId)
            ApprovalDecision.APPROVED
        }

        engine.run(gatedPipeline())

        assertNull(seenRunId.await())
    }
}
