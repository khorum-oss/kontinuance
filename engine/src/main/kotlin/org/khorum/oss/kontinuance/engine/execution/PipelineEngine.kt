package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.flow.Flow
import org.khorum.oss.kontinuance.dsl.model.Pipeline
import org.khorum.oss.kontinuance.dsl.model.Run
import org.khorum.oss.kontinuance.dsl.model.RunId
import org.khorum.oss.kontinuance.dsl.secret.EnvSecretSource
import org.khorum.oss.kontinuance.dsl.secret.SecretSource
import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.logging.StdoutLogSink

/**
 * Executes pipelines in-process and exposes their lifecycle.
 *
 * This is a consumer-facing contract (Constitution Principle I). The same [Pipeline] model is
 * produced by both the YAML descriptor and the Kotlin DSL, so both front-ends run through this
 * single engine path.
 */
interface PipelineEngine {

    /** Executes [pipeline] in-process and returns the completed [Run]. */
    suspend fun run(pipeline: Pipeline, secrets: SecretSource = EnvSecretSource()): Run

    /** Observes lifecycle transitions for the run identified by [runId] as they happen. */
    fun statuses(runId: RunId): Flow<StatusEvent>

    /** Requests cancellation of an in-flight run; the run then ends as `Cancelled`. */
    suspend fun cancel(runId: RunId)

    companion object {
        /**
         * The default engine: a single [RunStepExecutor] registered for the v0 `RunStep`,
         * streaming logs to [sink].
         */
        fun default(sink: LogSink = StdoutLogSink()): PipelineEngine =
            DefaultPipelineEngine(
                registry = StepExecutorRegistry(listOf(RunStepExecutor())),
                logSink = sink,
            )
    }
}
