package org.khorum.oss.kontinuance.engine.execution

import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.model.Step
import java.nio.file.Path

/**
 * Everything a [StepExecutor] needs to run one step, prepared by the engine.
 *
 * The engine owns and provides isolation, the scoped environment, and a masking log sink so
 * that every executor inherits those guarantees uniformly (see the DSL/engine API contract).
 *
 * @param step the step being executed.
 * @param workingDir the isolated, already-created working directory the command runs in.
 * @param environment the scoped environment (not the inherited process environment), with any
 *   resolved secrets injected.
 * @param logSink a masking sink; lines emitted through it are redacted before reaching stdout.
 */
data class StepContext(
    val step: Step,
    val workingDir: Path,
    val environment: Map<String, String>,
    val logSink: LogSink,
)
