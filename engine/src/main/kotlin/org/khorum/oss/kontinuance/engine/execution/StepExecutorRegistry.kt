package org.khorum.oss.kontinuance.engine.execution

import org.khorum.oss.kontinuance.dsl.model.StepDefinition

/**
 * Holds the registered [StepExecutor]s and selects one by step definition type.
 *
 * The first executor whose [StepExecutor.supports] returns true wins; if none match the step is
 * rejected with a clear [UnsupportedStepException] (FR-016).
 *
 * @param executors the registered executors, in priority order.
 */
class StepExecutorRegistry(private val executors: List<StepExecutor>) {

    init {
        require(executors.isNotEmpty()) { "at least one StepExecutor must be registered" }
    }

    /**
     * Returns the executor that supports [definition].
     *
     * @throws UnsupportedStepException when no registered executor matches.
     */
    fun executorFor(definition: StepDefinition): StepExecutor =
        executors.firstOrNull { it.supports(definition) }
            ?: throw UnsupportedStepException(definition)
}
