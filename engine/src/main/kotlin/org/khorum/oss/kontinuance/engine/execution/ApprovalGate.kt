package org.khorum.oss.kontinuance.engine.execution

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** The outcome of a manual-approval gate. */
enum class ApprovalDecision { APPROVED, REJECTED }

/**
 * Resolves a manual-approval gate (an [org.khorum.oss.kontinuance.engine.model.ApprovalStep]).
 *
 * [decide] is **non-blocking**: it returns the decision for this gate *now*, or `null` to signal that
 * no decision has been made yet — in which case the step ends `WaitingOnApproval` and the run **pauses**
 * (returns with completed stages preserved) rather than holding a coroutine open. A host resolves the
 * pause durably by persisting the paused run and later re-running it from its completed stages with the
 * decision available (see the durable-approval host gate).
 *
 * [runId] is the caller's run identifier (carried on the coroutine context via [ApprovalToken]) so an
 * external host can address the gate; it is `null` for non-interactive callers, which auto-approve.
 */
fun interface ApprovalGate {
    suspend fun decide(runId: String?, stepName: String): ApprovalDecision?
}

/**
 * The default gate for non-interactive hosts (CLI, tests): approves immediately so a gated pipeline
 * still runs to completion without an external approver — it never pauses.
 */
object AutoApprovingGate : ApprovalGate {
    override suspend fun decide(runId: String?, stepName: String): ApprovalDecision = ApprovalDecision.APPROVED
}

/**
 * Coroutine-context element carrying the host's run id into the engine, so an [ApprovalGate] can be
 * addressed by that id from outside (e.g. an approve endpoint). A host sets it around its
 * `engine.run(...)` call; structured concurrency propagates it to the step executors.
 */
class ApprovalToken(val runId: String) : AbstractCoroutineContextElement(ApprovalToken) {
    companion object Key : CoroutineContext.Key<ApprovalToken>
}
