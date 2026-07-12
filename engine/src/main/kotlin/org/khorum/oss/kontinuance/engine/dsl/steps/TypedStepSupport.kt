package org.khorum.oss.kontinuance.engine.dsl.steps

import org.khorum.oss.kontinuance.engine.model.SecretRef
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import org.khorum.oss.kontinuance.engine.model.StepDslBuilder

/**
 * Applies [name], the typed [definition], and the shared [options] envelope onto the generated
 * [StepDslBuilder], so the typed builders offer identical step configuration to the v0 `step { }`.
 */
internal fun StepDslBuilder.configureStep(
    name: String,
    definition: StepDefinition,
    options: TypedStepOptions,
) {
    this.name = name
    this.definition = definition
    this.timeout = options.timeout
    if (!options.enabled) condition(false)
    if (options.secrets.isNotEmpty()) {
        secrets { options.secrets.forEach { add(SecretRef(it)) } }
    }
    this.workingDirHint = options.workingDir
}
