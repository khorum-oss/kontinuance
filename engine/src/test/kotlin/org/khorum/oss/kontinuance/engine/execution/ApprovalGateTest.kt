package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.dsl.steps.approvalStep
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** Two gated stages, so a resume can skip the first and continue at the second. */
    private fun twoStagePipeline(): Pipeline = pipeline {
        name = "gated2"
        stages {
            stage { name = "prep"; steps { approvalStep("prep-gate") } }
            stage { name = "promote"; steps { approvalStep("promote-gate") } }
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
    fun `no decision yet pauses the run WaitingOnApproval, preserving completed stages`() = runBlocking {
        val engine = PipelineEngine.default(CapturingLogSink()) { _, _ -> null }

        val paused = engine.run(gatedPipeline())

        assertEquals(PipelineStatus.WaitingOnApproval, paused.status)
        assertEquals(PipelineStatus.WaitingOnApproval, paused.stageRuns[0].stepRuns[0].status)
    }

    @Test
    fun `the gate receives the ApprovalToken run id`() = runBlocking {
        var seenRunId: String? = "unset"
        val engine = PipelineEngine.default(CapturingLogSink()) { runId, _ ->
            seenRunId = runId
            ApprovalDecision.APPROVED
        }

        withContext(ApprovalToken("run-42")) { engine.run(gatedPipeline()) }

        assertEquals("run-42", seenRunId)
    }

    @Test
    fun `without an ApprovalToken the gate run id is null`() = runBlocking {
        var seenRunId: String? = "unset"
        val engine = PipelineEngine.default(CapturingLogSink()) { runId, _ ->
            seenRunId = runId
            ApprovalDecision.APPROVED
        }

        engine.run(gatedPipeline())

        assertNull(seenRunId)
    }

    @Test
    fun `resuming with completed stages skips them and continues at the paused gate`() = runBlocking {
        val asked = mutableListOf<String>()
        var promoteApproved = false
        val engine = PipelineEngine.default(CapturingLogSink()) { _, step ->
            asked.add(step)
            when {
                step == "promote-gate" && !promoteApproved -> null // pause on the first pass
                else -> ApprovalDecision.APPROVED
            }
        }

        // First pass: prep is approved, promote pauses → run WaitingOnApproval with prep completed.
        val paused = engine.run(twoStagePipeline())
        assertEquals(PipelineStatus.WaitingOnApproval, paused.status)
        val prep = paused.stageRuns.first { it.name == "prep" }
        assertEquals(PipelineStatus.Success, prep.status)

        // Resume: prep is supplied as completed and must not be re-run; promote is now approved.
        promoteApproved = true
        asked.clear()
        val resumed = engine.run(twoStagePipeline(), completedStages = listOf(prep))

        assertEquals(PipelineStatus.Success, resumed.status)
        assertFalse("prep-gate" in asked, "the completed prep stage should be skipped on resume")
        assertEquals(listOf("prep", "promote"), resumed.stageRuns.map { it.name })
    }
}
