package org.khorum.oss.kontinuance.server.trigger

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.execution.ApprovalDecision
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerApprovalGateTest {

    private fun storeWith(id: String) = InMemoryRunStore().apply {
        record(RunRecord(id = id, pipeline = "p", status = "Running"))
    }

    @Test
    fun `await marks WaitingOnApproval and approve resumes it, marking Running`() = runBlocking {
        val store = storeWith("run-1")
        val gate = ServerApprovalGate(store)

        val waiting = async { gate.await("run-1", "approve") }
        while (store.get("run-1")?.status != "WaitingOnApproval") yield()

        assertTrue(gate.approve("run-1"), "a pending gate should be approvable")
        assertEquals(ApprovalDecision.APPROVED, waiting.await())
        assertEquals("Running", store.get("run-1")?.status)
    }

    @Test
    fun `reject resolves the gate REJECTED`() = runBlocking {
        val store = storeWith("run-2")
        val gate = ServerApprovalGate(store)

        val waiting = async { gate.await("run-2", "approve") }
        while (store.get("run-2")?.status != "WaitingOnApproval") yield()

        assertTrue(gate.reject("run-2"))
        assertEquals(ApprovalDecision.REJECTED, waiting.await())
    }

    @Test
    fun `approve or reject with no run waiting returns false`() {
        val gate = ServerApprovalGate(storeWith("run-3"))
        assertFalse(gate.approve("run-3"), "nothing is waiting yet")
        assertFalse(gate.reject("absent"))
    }

    @Test
    fun `a null run id auto-approves without touching the store`() = runBlocking {
        val store = storeWith("run-4")
        val gate = ServerApprovalGate(store)

        assertEquals(ApprovalDecision.APPROVED, gate.await(null, "approve"))
        assertEquals("Running", store.get("run-4")?.status, "an unaddressable gate should not alter records")
    }
}
