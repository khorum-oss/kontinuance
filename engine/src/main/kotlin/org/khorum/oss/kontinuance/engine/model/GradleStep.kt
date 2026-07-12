package org.khorum.oss.kontinuance.engine.model

/**
 * Runs Gradle tasks as a first-class step, rather than a hand-written `run("./gradlew …")` shell
 * string. Executed by
 * [org.khorum.oss.kontinuance.engine.execution.steps.GradleStepExecutor] through the shared
 * `ProcessBuilder` path, so it inherits the same isolation, timeout, masking, and status lifecycle
 * as every other step type (the step-type seam, FR-016).
 *
 * @param tasks the Gradle tasks to run (non-empty), e.g. `["build"]`.
 * @param args extra Gradle arguments appended after the tasks, e.g. `["-x", "test", "--no-daemon"]`.
 * @param useWrapper prefer the project's `./gradlew` wrapper when present in the working directory;
 *   otherwise fall back to the system `gradle` on the `PATH`.
 */
data class GradleStep(
    val tasks: List<String>,
    val args: List<String> = emptyList(),
    val useWrapper: Boolean = true,
) : StepDefinition {
    init {
        require(tasks.isNotEmpty()) { "GradleStep requires at least one task" }
    }
}
