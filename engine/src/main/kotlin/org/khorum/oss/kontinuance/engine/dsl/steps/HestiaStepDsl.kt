package org.khorum.oss.kontinuance.engine.dsl.steps

import org.khorum.oss.kontinuance.engine.model.HestiaStep
import org.khorum.oss.kontinuance.engine.model.StepDslBuilder

/**
 * First-class khorum delivery steps inside a `steps { }` block — the typed counterparts to the descriptor's
 * `render:` / `deploy:` / `uat:` keys (roadmap item 7). Each wraps a khorum tool (`zosn`/`logos`/`euri`)
 * with a pass-through [args] list and the shared step envelope ([options]:
 * `timeout`/`enabled`/`secrets`/`workingDir`/`image`), identical to the v0 `step { }`.
 */

/** Declares a `zosn render …` step named [name] with pass-through [args]. */
fun StepDslBuilder.Group.renderStep(
    name: String,
    vararg args: String,
    options: TypedStepOptions = TypedStepOptions(),
) {
    step { configureStep(name, HestiaStep.render(args.toList()), options) }
}

/** Declares a `logos deploy …` step named [name] with pass-through [args]. */
fun StepDslBuilder.Group.deployStep(
    name: String,
    vararg args: String,
    options: TypedStepOptions = TypedStepOptions(),
) {
    step { configureStep(name, HestiaStep.deploy(args.toList()), options) }
}

/** Declares a `euri test …` (Playwright UAT) step named [name] with pass-through [args]. */
fun StepDslBuilder.Group.uatStep(
    name: String,
    vararg args: String,
    options: TypedStepOptions = TypedStepOptions(),
) {
    step { configureStep(name, HestiaStep.uat(args.toList()), options) }
}
