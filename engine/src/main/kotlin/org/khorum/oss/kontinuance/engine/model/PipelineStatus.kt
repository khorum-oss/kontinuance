package org.khorum.oss.kontinuance.engine.model

/**
 * Explicit lifecycle status for a run, stage, or step.
 *
 * Modeled as a sealed hierarchy (not an enum) so that data-carrying states such as
 * [Failed] can attach the offending step and a human-readable reason, and so callers
 * are forced to handle every case in an exhaustive `when`.
 */
sealed class PipelineStatus {
    /** Created but not yet scheduled. */
    object Pending : PipelineStatus()

    /** Scheduled, awaiting a concurrency permit. */
    object Queued : PipelineStatus()

    /** Actively executing. */
    object Running : PipelineStatus()

    /** Completed with no failures. */
    object Success : PipelineStatus()

    /**
     * Completed with a failure.
     *
     * @param step the name of the step that failed, or `null` when the failure is not
     *   attributable to a single step (e.g. an aggregate stage/run failure).
     * @param reason a human-readable explanation (never contains unmasked secrets).
     */
    data class Failed(val step: String?, val reason: String) : PipelineStatus()

    /** Terminated because cancellation was requested. */
    object Cancelled : PipelineStatus()

    /** Terminated because a deadline was exceeded. */
    object TimedOut : PipelineStatus()

    /** Bypassed because its condition was not met. */
    object Skipped : PipelineStatus()

    /** Reserved for a future manual-approval gate; not produced by any v0 step type. */
    object WaitingOnApproval : PipelineStatus()

    /** True once the status can no longer transition. */
    val isTerminal: Boolean
        get() = when (this) {
            Pending, Queued, Running, WaitingOnApproval -> false
            Success, Cancelled, TimedOut, Skipped, is Failed -> true
        }

    /** True for terminal states that represent an unsuccessful outcome. */
    val isFailure: Boolean
        get() = when (this) {
            Cancelled, TimedOut, is Failed -> true
            Pending, Queued, Running, WaitingOnApproval, Success, Skipped -> false
        }
}
