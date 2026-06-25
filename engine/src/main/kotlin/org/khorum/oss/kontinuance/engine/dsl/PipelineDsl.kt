package org.khorum.oss.kontinuance.engine.dsl

import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.SecretRef
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import kotlin.time.Duration

/**
 * Restricts the implicit-receiver scope of the pipeline DSL so an inner builder cannot
 * accidentally call an outer builder's members (the standard Kotlin type-safe-builder guard,
 * consistent with the Konstellation meta-DSL's `@KontinuanceDsl` marker in the `dsl` module).
 */
@DslMarker
annotation class KontinuanceEngineDsl

/**
 * Entry point for the Kotlin DSL escape hatch (Option C). Produces the **same** [Pipeline] model
 * as the equivalent YAML descriptor, so both front-ends run through one engine path
 * (FR-002, SC-002).
 *
 * ```kotlin
 * val pipeline = pipeline("build-and-test") {
 *     concurrency = 2
 *     stage("build") {
 *         step("compile") {
 *             run("./gradlew build")
 *             timeout = 5.minutes
 *         }
 *     }
 * }
 * ```
 */
fun pipeline(name: String, block: PipelineBuilder.() -> Unit): Pipeline =
    PipelineBuilder(name).apply(block).build()

/** Builder for a [Pipeline]. */
@KontinuanceEngineDsl
class PipelineBuilder(private val name: String) {
    /** Maximum number of simultaneously RUNNING steps (K); must be >= 1. */
    var concurrency: Int = 1

    private val stages = mutableListOf<Stage>()

    /** Declares a stage, in order. */
    fun stage(name: String, block: StageBuilder.() -> Unit) {
        stages.add(StageBuilder(name).apply(block).build())
    }

    internal fun build(): Pipeline = Pipeline(name, stages.toList(), concurrency)
}

/** Builder for a [Stage]. */
@KontinuanceEngineDsl
class StageBuilder(private val name: String) {
    private val steps = mutableListOf<Step>()

    /** Declares a step, in order. */
    fun step(name: String, block: StepBuilder.() -> Unit) {
        steps.add(StepBuilder(name).apply(block).build())
    }

    internal fun build(): Stage = Stage(name, steps.toList())
}

/** Builder for a [Step]. */
@KontinuanceEngineDsl
class StepBuilder(private val name: String) {
    /** Optional per-step deadline; must be > 0 when set. */
    var timeout: Duration? = null

    /** When `false`, the step is skipped and its stage continues. */
    var condition: Boolean = true

    /** Optional relative subdirectory resolved inside the step's isolated working directory. */
    var workingDir: String? = null

    private var command: String? = null
    private val secrets = mutableListOf<SecretRef>()

    /** Sets the shell command this step runs (required). */
    fun run(command: String) {
        this.command = command
    }

    /** Names secrets to inject into the step's scoped environment and mask in logs. */
    fun secrets(vararg names: String) {
        names.forEach { secrets.add(SecretRef(it)) }
    }

    internal fun build(): Step {
        val resolvedCommand = requireNotNull(command) {
            "step '$name' must declare a command via run(...)"
        }
        return Step(
            name = name,
            definition = RunStep(resolvedCommand),
            timeout = timeout,
            condition = condition,
            secrets = secrets.toList(),
            workingDirHint = workingDir,
        )
    }
}
