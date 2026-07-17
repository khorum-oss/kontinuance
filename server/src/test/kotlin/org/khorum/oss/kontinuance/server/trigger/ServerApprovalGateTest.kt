package org.khorum.oss.kontinuance.server.trigger

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.execution.ApprovalDecision
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerApprovalGateTest {

    @Test
    fun `decide pauses (null) until a decision is granted, then consumes it once`() = runBlocking {
        val gate = ServerApprovalGate()

        assertNull(gate.decide("run-1", "gate"), "no grant yet → pause")

        gate.grant("run-1", ApprovalDecision.APPROVED)
        assertEquals(ApprovalDecision.APPROVED, gate.decide("run-1", "gate"), "consumes the grant")
        assertNull(gate.decide("run-1", "gate"), "one-shot: the grant is gone")
    }

    @Test
    fun `a granted rejection is returned`() = runBlocking {
        val gate = ServerApprovalGate()
        gate.grant("run-2", ApprovalDecision.REJECTED)
        assertEquals(ApprovalDecision.REJECTED, gate.decide("run-2", "gate"))
    }

    @Test
    fun `a null run id auto-approves`() = runBlocking {
        assertEquals(ApprovalDecision.APPROVED, ServerApprovalGate().decide(null, "gate"))
    }
}
