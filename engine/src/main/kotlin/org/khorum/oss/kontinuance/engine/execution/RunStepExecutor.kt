package org.khorum.oss.kontinuance.engine.execution

import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import java.nio.file.Path
import kotlin.time.Duration

/**
 * The v0 [StepExecutor]: runs a [RunStep] shell command via `ProcessBuilder`.
 *
 * It launches `/bin/sh -c <command>`; all of the process lifecycle — isolation, scoped environment,
 * streamed-and-masked output, timeout, process-tree cleanup, and status mapping — lives in the
 * shared [ProcessStepExecutor] base, which the typed step executors (`gradle`/`docker`/`npm`) reuse
 * unchanged.
 *
 * @param defaultTimeout applied when a step declares no explicit timeout.
 */
class RunStepExecutor(
    defaultTimeout: Duration = DEFAULT_TIMEOUT,
) : ProcessStepExecutor(defaultTimeout) {

    override fun supports(definition: StepDefinition): Boolean = definition is RunStep

    override fun command(step: Step, workingDir: Path): List<String> =
        listOf("/bin/sh", "-c", (step.definition as RunStep).command)
}
