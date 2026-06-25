package org.khorum.oss.kontinuance.engine.execution

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipelineStatusTest {

    @Test
    fun `non-terminal states are not terminal`() {
        val nonTerminal = listOf(
            PipelineStatus.Pending,
            PipelineStatus.Queued,
            PipelineStatus.Running,
            PipelineStatus.WaitingOnApproval,
        )
        nonTerminal.forEach { assertFalse(it.isTerminal, "$it should not be terminal") }
    }

    @Test
    fun `terminal states are terminal`() {
        val terminal = listOf(
            PipelineStatus.Success,
            PipelineStatus.Cancelled,
            PipelineStatus.TimedOut,
            PipelineStatus.Skipped,
            PipelineStatus.Failed(step = "compile", reason = "exit 1"),
        )
        terminal.forEach { assertTrue(it.isTerminal, "$it should be terminal") }
    }

    @Test
    fun `failed carries the offending step and reason`() {
        val failed = PipelineStatus.Failed(step = "unit", reason = "exited with code 2")
        assertEquals("unit", failed.step)
        assertEquals("exited with code 2", failed.reason)
        assertTrue(failed.isFailure)
    }

    @Test
    fun `success and skipped are not failures`() {
        assertFalse(PipelineStatus.Success.isFailure)
        assertFalse(PipelineStatus.Skipped.isFailure)
    }

    @Test
    fun `cancelled and timed out are failures`() {
        assertTrue(PipelineStatus.Cancelled.isFailure)
        assertTrue(PipelineStatus.TimedOut.isFailure)
    }
}
