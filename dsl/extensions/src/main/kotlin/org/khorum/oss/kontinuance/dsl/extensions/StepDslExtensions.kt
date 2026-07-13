package org.khorum.oss.kontinuance.dsl.extensions

import org.khorum.oss.kontinuance.dsl.model.RunStep
import org.khorum.oss.kontinuance.dsl.model.SecretRef
import org.khorum.oss.kontinuance.dsl.model.StepDslBuilder

/**
 * Convenience for the common v0 step type: sets the step's [definition] to a [RunStep] for [command].
 *
 * Equivalent to `definition = RunStep(command)`, but reads like the descriptor's `run:` key.
 */
fun StepDslBuilder.run(command: String) {
    definition = RunStep(command)
}

/** Registers secret references by name, sparing callers from wrapping each in [SecretRef]. */
fun StepDslBuilder.secrets(vararg names: String) {
    secrets { names.forEach { add(SecretRef(it)) } }
}