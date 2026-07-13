package org.khorum.oss.kontinuance.engine.execution

import org.khorum.oss.kontinuance.dsl.model.StepDefinition
import org.khorum.oss.kontinuance.dsl.model.StepRun

/**
 * Runs a step of a particular [StepDefinition] type.
 *
 * The engine never hard-codes "run a shell command"; it selects, from a registry, the executor
 * whose [supports] matches the step's definition and calls [execute]. Adding a step type means
 * adding a [StepDefinition] subtype plus a matching executor — the engine loop is unchanged
 * (FR-016).
 */
interface StepExecutor {
    /** True if this executor can run the given [definition]. */
    fun supports(definition: StepDefinition): Boolean

    /** Executes the step described by [context] and returns its terminal [StepRun]. */
    suspend fun execute(context: StepContext): StepRun
}
