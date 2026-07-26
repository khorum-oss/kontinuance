package org.khorum.oss.kontinuance.engine.execution.steps

import org.khorum.oss.kontinuance.engine.execution.DockerStepSandbox
import org.khorum.oss.kontinuance.engine.execution.ProcessStepExecutor
import org.khorum.oss.kontinuance.engine.execution.StepSandbox
import org.khorum.oss.kontinuance.engine.model.HestiaStep
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import java.nio.file.Path

/**
 * Runs a [HestiaStep] by invoking its khorum delivery tool — `zosn render`, `logos deploy`, or `euri test`
 * — with the step's pass-through arguments. One executor fronts the whole tool family (mirroring how
 * [NpmStepExecutor]/[DockerStepExecutor] front theirs), dispatching on [HestiaStep.tool]. A missing tool
 * binary surfaces as a FAILED step naming that binary (via the shared [ProcessStepExecutor] launch
 * handling); the actual tools live in the khorum hub and are exercised on real delivery hosts.
 */
class HestiaStepExecutor(sandbox: StepSandbox = DockerStepSandbox()) : ProcessStepExecutor(sandbox = sandbox) {

    override fun supports(definition: StepDefinition): Boolean = definition is HestiaStep

    override fun command(step: Step, workingDir: Path): List<String> = argv(step.definition as HestiaStep)

    companion object {
        /** The `<binary> <command> <args…>` argv for [hestia]. Pure. */
        fun argv(hestia: HestiaStep): List<String> =
            listOf(hestia.tool.binary, hestia.tool.command) + hestia.args
    }
}
