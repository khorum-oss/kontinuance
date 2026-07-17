package org.khorum.oss.kontinuance.engine.model

/**
 * A manual-approval gate: the run pauses at this step (reported [PipelineStatus.WaitingOnApproval] by
 * an interactive host) until an external approver approves or rejects it. Approval lets the run
 * continue; rejection ends the run [PipelineStatus.Cancelled] (a deliberate stop, not a failure).
 *
 * Unlike the other step types this executes no process — it is resolved by an
 * [org.khorum.oss.kontinuance.engine.execution.ApprovalGate] injected into the engine. Non-interactive
 * hosts (the CLI, tests) use the auto-approving gate so gated pipelines still run to completion; a
 * server host supplies a gate backed by its approve/reject endpoints.
 *
 * @param message the human-readable prompt shown to the approver.
 */
data class ApprovalStep(val message: String = DEFAULT_MESSAGE) : StepDefinition {
    companion object {
        const val DEFAULT_MESSAGE = "manual approval required"
    }
}
