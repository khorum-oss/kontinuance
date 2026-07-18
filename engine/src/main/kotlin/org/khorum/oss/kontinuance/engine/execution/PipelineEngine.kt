package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.flow.Flow
import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.logging.StdoutLogSink
import org.khorum.oss.kontinuance.engine.execution.steps.ApprovalStepExecutor
import org.khorum.oss.kontinuance.engine.execution.steps.DockerStepExecutor
import org.khorum.oss.kontinuance.engine.execution.steps.GitStepExecutor
import org.khorum.oss.kontinuance.engine.execution.steps.GradleStepExecutor
import org.khorum.oss.kontinuance.engine.execution.steps.NpmStepExecutor
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.engine.secret.EnvSecretSource
import org.khorum.oss.kontinuance.engine.secret.SecretSource

/**
 * Executes pipelines in-process and exposes their lifecycle.
 *
 * This is a consumer-facing contract (Constitution Principle I). The same [Pipeline] model is
 * produced by both the YAML descriptor and the Kotlin DSL, so both front-ends run through this
 * single engine path.
 */
interface PipelineEngine {

    /**
     * Executes [pipeline] in-process and returns the [Run]. Ordinarily the returned run is terminal;
     * if it reaches a manual-approval gate with no decision yet it returns paused
     * ([org.khorum.oss.kontinuance.engine.model.PipelineStatus.WaitingOnApproval]) with the stages
     * completed so far.
     *
     * [completedStages] resumes a previously paused run: stages already completed (matched by name) are
     * skipped and reused, so execution continues from the gate. Pass the empty list for a fresh run.
     */
    suspend fun run(
        pipeline: Pipeline,
        secrets: SecretSource = EnvSecretSource(),
        completedStages: List<StageRun> = emptyList(),
    ): Run

    /** Observes lifecycle transitions for the run identified by [runId] as they happen. */
    fun statuses(runId: RunId): Flow<StatusEvent>

    /** Requests cancellation of an in-flight run; the run then ends as `Cancelled`. */
    suspend fun cancel(runId: RunId)

    companion object {
        /**
         * The default engine, streaming logs to [sink]. It registers every built-in step executor:
         * the v0 [RunStepExecutor] for `RunStep`, the typed [GradleStepExecutor], [DockerStepExecutor],
         * and [NpmStepExecutor], and the [ApprovalStepExecutor] for manual gates — the last backed by
         * [approvalGate], which defaults to [AutoApprovingGate] so non-interactive hosts still run
         * gated pipelines to completion.
         */
        fun default(
            sink: LogSink = StdoutLogSink(),
            approvalGate: ApprovalGate = AutoApprovingGate,
        ): PipelineEngine =
            DefaultPipelineEngine(
                registry = StepExecutorRegistry(
                    listOf(
                        RunStepExecutor(),
                        GradleStepExecutor(),
                        DockerStepExecutor(),
                        NpmStepExecutor(),
                        GitStepExecutor(),
                        ApprovalStepExecutor(approvalGate),
                    ),
                ),
                logSink = sink,
            )
    }
}
