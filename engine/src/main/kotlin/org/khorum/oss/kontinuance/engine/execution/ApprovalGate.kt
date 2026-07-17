package org.khorum.oss.kontinuance.engine.execution

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** The outcome of a manual-approval gate. */
enum class ApprovalDecision { APPROVED, REJECTED }

/**
 * Resolves a manual-approval gate (an [org.khorum.oss.kontinuance.engine.model.ApprovalStep]). The
 * executor suspends on [await] until an approver decides; the engine keeps the run's coroutine alive
 * while it waits, so no run state has to be serialized for the common in-process case.
 *
 * [runId] is the caller's run identifier (carried on the coroutine context via [ApprovalToken]) so an
 * external host can address the waiting gate; it is `null` for non-interactive callers.
 */
fun interface ApprovalGate {
    suspend fun await(runId: String?, stepName: String): ApprovalDecision
}

/**
 * The default gate for non-interactive hosts (CLI, tests): approves immediately so a gated pipeline
 * still runs to completion without an external approver.
 */
object AutoApprovingGate : ApprovalGate {
    override suspend fun await(runId: String?, stepName: String): ApprovalDecision = ApprovalDecision.APPROVED
}

/**
 * Coroutine-context element carrying the host's run id into the engine, so an [ApprovalGate] can be
 * addressed by that id from outside (e.g. an approve endpoint). A host sets it around its
 * `engine.run(...)` call; structured concurrency propagates it to the step executors.
 */
class ApprovalToken(val runId: String) : AbstractCoroutineContextElement(ApprovalToken) {
    companion object Key : CoroutineContext.Key<ApprovalToken>
}
