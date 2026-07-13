package org.khorum.oss.kontinuance.dsl.model

import org.khorum.oss.konstellation.metaDsl.annotation.SingleEntryTransformDsl

/**
 * The typed payload describing what a [Step] executes.
 *
 * This is the **step-type seam** (FR-016): execution is dispatched by definition type,
 * so adding a new kind of step (e.g. a future `GradleStep`, `DockerStep`, `NpmStep`) is
 * additive — a new sealed subtype plus a matching
 * [org.khorum.oss.kontinuance.engine.execution.StepExecutor] — and never requires
 * changing the engine's stage/step loop.
 */
sealed interface StepDefinition

/**
 * Runs a shell command. The v0 step type, executed via `ProcessBuilder`.
 *
 * @param command the shell command line to run (e.g. `"./gradlew build"`).
 */
@SingleEntryTransformDsl<String>(String::class, "RunStep(%N)")
data class RunStep(val command: String) : StepDefinition {
    init {
        require(command.isNotBlank()) { "RunStep command must be non-empty" }
    }
}
