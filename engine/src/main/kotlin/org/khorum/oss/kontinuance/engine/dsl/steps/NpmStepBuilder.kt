package org.khorum.oss.kontinuance.engine.dsl.steps

import org.khorum.oss.kontinuance.engine.dsl.KontinuanceDsl
import org.khorum.oss.kontinuance.engine.model.NpmStep
import org.khorum.oss.kontinuance.engine.model.StepDslBuilder

/**
 * Builder for an [npmStep]: declares **exactly one** of [script], [install], or [installClean],
 * mirroring the descriptor's `npm: { script: … }` / `npm: { install: … }` shape.
 */
@KontinuanceDsl
class NpmStepBuilder {
    private var definition: NpmStep? = null

    /** Runs `npm run <script>`. */
    fun script(script: String) {
        set(NpmStep.script(script))
    }

    /** Installs dependencies: `npm ci` when [clean] (default), else `npm install`. */
    fun install(clean: Boolean = true) {
        set(NpmStep.install(clean))
    }

    /** Shorthand for a clean, reproducible install (`npm ci`). */
    fun installClean() {
        install(clean = true)
    }

    private fun set(step: NpmStep) {
        require(definition == null) { "npmStep declares exactly one of script(...) or install(...)" }
        definition = step
    }

    internal fun definition(): NpmStep =
        requireNotNull(definition) { "npmStep requires a script(...) or install(...) call" }
}

/**
 * Declares a first-class NPM step named [name] inside a `steps { }` block — the typed counterpart to
 * the descriptor's `npm:` key. [options] carries the shared step envelope
 * (`timeout`/`enabled`/`secrets`/`workingDir`), identical to the v0 `step { }`.
 */
fun StepDslBuilder.Group.npmStep(
    name: String,
    options: TypedStepOptions = TypedStepOptions(),
    block: NpmStepBuilder.() -> Unit,
) {
    val definition = NpmStepBuilder().apply(block).definition()
    step { configureStep(name, definition, options) }
}
