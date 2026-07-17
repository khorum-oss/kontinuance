package org.khorum.oss.kontinuance.engine.dsl.steps

import org.khorum.oss.kontinuance.engine.model.ApprovalStep
import org.khorum.oss.kontinuance.engine.model.StepDslBuilder

/**
 * Declares a manual-approval gate named [name] inside a `steps { }` block — the typed counterpart to
 * the descriptor's `approval:` key. The run pauses at this step until it is approved or rejected;
 * [message] is the prompt shown to the approver. [options] carries the shared step envelope
 * (`timeout`/`enabled`/`secrets`/`workingDir`), identical to the v0 `step { }`.
 */
fun StepDslBuilder.Group.approvalStep(
    name: String,
    message: String = ApprovalStep.DEFAULT_MESSAGE,
    options: TypedStepOptions = TypedStepOptions(),
) {
    step { configureStep(name, ApprovalStep(message), options) }
}
