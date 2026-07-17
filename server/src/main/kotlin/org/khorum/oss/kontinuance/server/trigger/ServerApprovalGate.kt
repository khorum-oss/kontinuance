package org.khorum.oss.kontinuance.server.trigger

import kotlinx.coroutines.CompletableDeferred
import org.khorum.oss.kontinuance.engine.execution.ApprovalDecision
import org.khorum.oss.kontinuance.engine.execution.ApprovalGate
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The server's [ApprovalGate]: when a triggered run reaches a manual-approval step it suspends here,
 * and its record is marked `WaitingOnApproval` (so the UI shows the gate and the approve/reject
 * actions). [ApprovalController] resolves the waiting gate by run id via [approve] / [reject].
 *
 * Because stages run in order, a run waits at one gate at a time, so the run id alone keys the pending
 * decision. This is an in-process gate: it only resolves runs whose coroutine is still alive on this
 * instance (the same constraint as the manual trigger). A `null` run id (no [ApprovalToken] set)
 * auto-approves rather than hanging.
 */
@Component
class ServerApprovalGate(private val store: RunStore) : ApprovalGate {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<ApprovalDecision>>()

    override suspend fun await(runId: String?, stepName: String): ApprovalDecision {
        if (runId == null) return ApprovalDecision.APPROVED
        val decision = CompletableDeferred<ApprovalDecision>()
        pending[runId] = decision
        markStatus(runId, WAITING)
        try {
            val outcome = decision.await()
            if (outcome == ApprovalDecision.APPROVED) markStatus(runId, RUNNING)
            return outcome
        } finally {
            pending.remove(runId, decision)
        }
    }

    /** Approves the run waiting at [runId]'s gate; `true` if one was pending. */
    fun approve(runId: String): Boolean = decide(runId, ApprovalDecision.APPROVED)

    /** Rejects the run waiting at [runId]'s gate; `true` if one was pending. */
    fun reject(runId: String): Boolean = decide(runId, ApprovalDecision.REJECTED)

    private fun decide(runId: String, outcome: ApprovalDecision): Boolean =
        pending[runId]?.complete(outcome) ?: false

    private fun markStatus(runId: String, status: String) {
        store.get(runId)?.let { store.record(it.copy(status = status)) }
    }

    private companion object {
        const val WAITING = "WaitingOnApproval"
        const val RUNNING = "Running"
    }
}
