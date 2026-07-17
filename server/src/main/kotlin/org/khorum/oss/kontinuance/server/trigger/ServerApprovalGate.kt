package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.engine.execution.ApprovalDecision
import org.khorum.oss.kontinuance.engine.execution.ApprovalGate
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The server's [ApprovalGate]. On the initial run a gate has no decision, so [decide] returns `null`
 * and the run pauses (`WaitingOnApproval`) — the pause is persisted, not held in memory. When an
 * operator approves/rejects, [RunApprovals] records a one-shot decision here via [grant] and re-runs the
 * pipeline from its completed stages; on that resume [decide] consumes the grant so the gate proceeds.
 *
 * Because the durable state lives in the run store (not this map), approval works even after a restart:
 * a fresh gate instance with an empty map resolves a paused run just the same. A `null` run id (no
 * [org.khorum.oss.kontinuance.engine.execution.ApprovalToken]) auto-approves rather than pausing.
 */
@Component
class ServerApprovalGate : ApprovalGate {

    private val granted = ConcurrentHashMap<String, ApprovalDecision>()

    override suspend fun decide(runId: String?, stepName: String): ApprovalDecision? {
        if (runId == null) return ApprovalDecision.APPROVED
        return granted.remove(runId) // one-shot grant, or null to pause when none is set
    }

    /** Records a one-shot [decision] to be consumed by [runId]'s next gate on resume. */
    fun grant(runId: String, decision: ApprovalDecision) {
        granted[runId] = decision
    }
}
