package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.engine.execution.ApprovalToken
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.domain.RecordingLogSink
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Runs a pipeline on the engine in the background under a caller-chosen run id and records the result
 * under that id — whether it finishes (terminal) or pauses at a manual-approval gate
 * (`WaitingOnApproval`, with its completed stages persisted). Shared by the initial trigger and the
 * approve-resume path so both take the same execution route.
 *
 * The run id is carried into the engine via [ApprovalToken] so an approval gate can be addressed by it.
 * [completedStages] resumes a paused run: those stages are skipped and reused rather than re-executed.
 * [context] is the run's repository, commit, and project (033/041/039), carried onto every record this
 * writes so finishing a run never erases what starting it recorded.
 */
@Component
class RunLauncher(
    private val store: RunStore,
    private val engine: PipelineEngine,
    private val scope: CoroutineScope,
    private val logStore: RunLogStore,
) {
    fun launch(
        id: String,
        pipeline: Pipeline,
        startedAt: Instant,
        completedStages: List<StageRun> = emptyList(),
        context: RunContext = RunContext(),
    ) {
        scope.launch {
            val record = runCatching {
                withContext(ApprovalToken(id)) {
                    // Record this run's masked output under its id (018) so the UI can show real logs.
                    // Pass the server's id as the engine run id so a cancel request can address it (028).
                    val run = engine.run(
                        pipeline,
                        completedStages = completedStages,
                        logSink = RecordingLogSink(id, logStore),
                        runId = RunId(id),
                    )
                    // The terminal record REPLACES the `Running` one by id, so anything the engine cannot
                    // report has to be restored here or finishing a run erases it. The engine knows
                    // nothing about the repository (033), the commit the checkout was pinned to (041), or
                    // the project the run was launched under beyond the descriptor's own `project:` key
                    // (039) — and `startedAt`, which it derives from step timings, is absent for a run
                    // whose steps never started.
                    val recorded = RunRecord.from(run, Instant.now(), trigger = "manual")
                    recorded.copy(
                        id = id,
                        repo = context.repo,
                        sha = context.sha,
                        project = recorded.project ?: context.project,
                        startedAt = recorded.startedAt ?: startedAt,
                    )
                }
            }.getOrElse {
                RunRecord(
                    id = id,
                    pipeline = pipeline.name,
                    status = "Failed",
                    reason = it.message,
                    startedAt = startedAt,
                    endedAt = Instant.now(),
                    repo = context.repo,
                    sha = context.sha,
                    trigger = "manual",
                    project = context.project,
                    stages = RunRecord.skeleton(pipeline),
                )
            }
            store.record(record)
        }
    }
}
