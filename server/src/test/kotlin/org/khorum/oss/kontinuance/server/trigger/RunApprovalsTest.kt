package org.khorum.oss.kontinuance.server.trigger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.engine.execution.ApprovalDecision
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.persistence.InMemoryRunLogStore
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the durable approve/resume path with a real engine and a gated descriptor, using an
 * [Dispatchers.Unconfined] scope so each background run executes inline (the paused/terminal record is
 * observable without waiting).
 */
class RunApprovalsTest {

    private val gatedDescriptor = """
        pipeline:
          name: "promote-demo"
          stages:
            - name: "gate"
              steps:
                - name: "approve"
                  approval: "Promote to production?"
    """.trimIndent()

    /** A fresh set of components sharing [store] — a new one models a server restart. */
    private class Host(store: RunStore, descriptor: Path) {
        val gate = ServerApprovalGate()
        val launcher = RunLauncher(
            store,
            PipelineEngine.default(LogSink { }, gate),
            CoroutineScope(Dispatchers.Unconfined),
            InMemoryRunLogStore(),
        )
        val trigger = RunTrigger(store, launcher, descriptor.toString())
        val approvals = RunApprovals(store, gate, launcher, descriptor.toString())
    }

    private fun descriptorIn(dir: Path): Path {
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, gatedDescriptor)
        return file
    }

    @Test
    fun `a gated run pauses WaitingOnApproval, then approving resumes it to Success`(@TempDir dir: Path) {
        val store = InMemoryRunStore()
        val host = Host(store, descriptorIn(dir))

        val result = host.trigger.trigger()
        assertTrue(result is RunTrigger.Result.Accepted)
        val id = result.id

        val paused = assertNotNull(store.get(id), "the run should be recorded")
        assertEquals("WaitingOnApproval", paused.status, "a gate with no decision pauses the run")

        assertTrue(host.approvals.resolve(id, ApprovalDecision.APPROVED))
        assertEquals("Success", assertNotNull(store.get(id)).status, "approval resumes the run to completion")
    }

    @Test
    fun `rejecting a paused run ends it Cancelled`(@TempDir dir: Path) {
        val store = InMemoryRunStore()
        val host = Host(store, descriptorIn(dir))
        val id = (host.trigger.trigger() as RunTrigger.Result.Accepted).id
        assertEquals("WaitingOnApproval", assertNotNull(store.get(id)).status)

        assertTrue(host.approvals.resolve(id, ApprovalDecision.REJECTED))
        assertEquals("Cancelled", assertNotNull(store.get(id)).status)
    }

    @Test
    fun `resolving returns false when no run is waiting`(@TempDir dir: Path) {
        val host = Host(InMemoryRunStore(), descriptorIn(dir))
        assertFalse(host.approvals.resolve("absent", ApprovalDecision.APPROVED))
    }

    @Test
    fun `approval works after a restart - a fresh host resolves the persisted paused run`(@TempDir dir: Path) {
        val store = InMemoryRunStore()
        val descriptor = descriptorIn(dir)

        // First host triggers the run and it pauses; then the "process restarts".
        val id = (Host(store, descriptor).trigger.trigger() as RunTrigger.Result.Accepted).id
        assertEquals("WaitingOnApproval", assertNotNull(store.get(id)).status)

        // A brand-new host (empty in-memory gate) resolves it purely from the persisted store state.
        val afterRestart = Host(store, descriptor)
        assertTrue(afterRestart.approvals.resolve(id, ApprovalDecision.APPROVED))
        assertEquals("Success", assertNotNull(store.get(id)).status)
    }
}
