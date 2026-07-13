package org.khorum.oss.kontinuance.engine.execution.steps

import org.khorum.oss.kontinuance.engine.execution.ProcessStepExecutor
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import org.khorum.oss.kontinuance.engine.model.GradleStep
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs a [GradleStep] by launching `gradle`/`./gradlew` with the step's tasks and args. The wrapper
 * is preferred when [GradleStep.useWrapper] is set and a `gradlew` script is present in the step's
 * working directory; otherwise the system `gradle` on the `PATH` is used. A missing binary surfaces
 * as a FAILED step naming `gradle` (via the shared [ProcessStepExecutor] launch handling), not an
 * exception.
 */
class GradleStepExecutor : ProcessStepExecutor() {

    override fun supports(definition: StepDefinition): Boolean = definition is GradleStep

    override fun command(step: Step, workingDir: Path): List<String> {
        val gradle = step.definition as GradleStep
        return argv(gradle, wrapperPresent = wrapperPresent(workingDir))
    }

    private fun wrapperPresent(workingDir: Path): Boolean =
        Files.isRegularFile(workingDir.resolve(WRAPPER))

    companion object {
        private const val WRAPPER = "gradlew"

        /**
         * The argv for [gradle]: `[gradleBin] + tasks + args`, where `gradleBin` is `./gradlew` when
         * [wrapperPresent] and the step opts into the wrapper, else the system `gradle`. Pure.
         */
        fun argv(gradle: GradleStep, wrapperPresent: Boolean): List<String> {
            val gradleBin = if (gradle.useWrapper && wrapperPresent) "./$WRAPPER" else "gradle"
            return listOf(gradleBin) + gradle.tasks + gradle.args
        }
    }
}
