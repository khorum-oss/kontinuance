package org.khorum.oss.kontinuance.engine.execution.steps

import org.khorum.oss.kontinuance.engine.execution.StepContext
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import java.nio.file.Files

/**
 * A [StepContext] whose working directory does not exist, so launching the step's tool fails at
 * `ProcessBuilder.start()` regardless of what is installed on the host — deterministically
 * exercising the "launch failure ⇒ FAILED, naming the tool, not an exception" path (FR-007/SC-004).
 * (An absent-`PATH` scoped environment would not do it: the JVM resolves a bare command name against
 * the *parent* process `PATH`, not the child's.) The `IOException` message names the program.
 */
internal fun missingToolContext(step: Step): StepContext {
    val nonexistentDir = Files.createTempDirectory("knt-launch-fail-").resolve("does-not-exist")
    return StepContext(
        step = step,
        workingDir = nonexistentDir,
        environment = emptyMap(),
        logSink = CapturingLogSink(),
    )
}
