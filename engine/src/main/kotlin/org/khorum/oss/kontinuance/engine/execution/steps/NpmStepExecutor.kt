package org.khorum.oss.kontinuance.engine.execution.steps

import org.khorum.oss.kontinuance.engine.execution.ProcessStepExecutor
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import org.khorum.oss.kontinuance.engine.model.NpmMode
import org.khorum.oss.kontinuance.engine.model.NpmStep
import java.nio.file.Path

/**
 * Runs an [NpmStep] by invoking `npm`: `npm run <script>` for [NpmMode.SCRIPT], or `npm ci` /
 * `npm install` for [NpmMode.INSTALL] depending on [NpmStep.clean]. A missing `npm` binary surfaces
 * as a FAILED step naming `npm` (via the shared [ProcessStepExecutor] launch handling).
 */
class NpmStepExecutor : ProcessStepExecutor() {

    override fun supports(definition: StepDefinition): Boolean = definition is NpmStep

    override fun command(step: Step, workingDir: Path): List<String> = argv(step.definition as NpmStep)

    companion object {
        /** The `npm` argv for [npm], driven by its [NpmStep.mode]. Pure. */
        fun argv(npm: NpmStep): List<String> = when (npm.mode) {
            NpmMode.SCRIPT -> listOf("npm", "run", npm.script)
            NpmMode.INSTALL -> listOf("npm", if (npm.clean) "ci" else "install")
        }
    }
}
