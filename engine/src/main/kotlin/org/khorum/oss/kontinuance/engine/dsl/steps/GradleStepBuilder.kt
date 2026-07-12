package org.khorum.oss.kontinuance.engine.dsl.steps

import org.khorum.oss.kontinuance.engine.dsl.KontinuanceDsl
import org.khorum.oss.kontinuance.engine.model.GradleStep
import org.khorum.oss.kontinuance.engine.model.StepDslBuilder

/**
 * Builder for the Gradle-specific configuration of a [gradleStep]. Yields the same [GradleStep]
 * model as the descriptor's `gradle:` key (FR-005).
 */
@KontinuanceDsl
class GradleStepBuilder {
    private val tasks = mutableListOf<String>()
    private val args = mutableListOf<String>()

    /** Prefer `./gradlew` over the system `gradle` when a wrapper is present (default `true`). */
    var useWrapper: Boolean = true

    /** Adds Gradle tasks to run, e.g. `tasks("build")`; at least one is required. */
    fun tasks(vararg tasks: String) {
        this.tasks += tasks
    }

    /** Adds extra Gradle arguments appended after the tasks, e.g. `args("-x", "test")`. */
    fun args(vararg args: String) {
        this.args += args
    }

    internal fun build(): GradleStep = GradleStep(tasks.toList(), args.toList(), useWrapper)
}

/**
 * Declares a first-class Gradle step named [name] inside a `steps { }` block — the typed
 * counterpart to the descriptor's `gradle:` key. [options] carries the shared step envelope
 * (`timeout`/`enabled`/`secrets`/`workingDir`), identical to the v0 `step { }`.
 */
fun StepDslBuilder.Group.gradleStep(
    name: String,
    options: TypedStepOptions = TypedStepOptions(),
    block: GradleStepBuilder.() -> Unit,
) {
    val definition = GradleStepBuilder().apply(block).build()
    step { configureStep(name, definition, options) }
}
