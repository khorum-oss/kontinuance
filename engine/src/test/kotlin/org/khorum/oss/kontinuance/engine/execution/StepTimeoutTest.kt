package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class StepTimeoutTest {

    @Test
    fun `a step exceeding its timeout is killed promptly and marked TimedOut`() = runBlocking {
        val pipeline = Pipeline(
            name = "p",
            stages = listOf(
                Stage("s", listOf(Step("sleeper", RunStep("sleep 30"), timeout = 500.milliseconds))),
            ),
        )

        val startNanos = System.nanoTime()
        val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

        val stepRun = run.stageRuns.single().stepRuns.single()
        assertEquals(PipelineStatus.TimedOut, stepRun.status)
        // The 30s sleep must have been terminated within ~1s of its 500ms deadline (SC-005),
        // nowhere near its natural completion.
        assertTrue(elapsedMs < TERMINATION_BUDGET_MS, "timeout took too long: ${elapsedMs}ms")

        // No orphaned sleep process should survive.
        delay(STABILISE_MS)
        val orphan = ProcessHandle.allProcesses().anyMatch { handle ->
            handle.info().commandLine().map { it.contains("sleep 30") }.orElse(false)
        }
        assertFalse(orphan, "an orphaned 'sleep 30' process survived termination")
    }

    private companion object {
        const val TERMINATION_BUDGET_MS = 3_000L
        const val STABILISE_MS = 300L
    }
}
