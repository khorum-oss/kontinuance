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
 * [context] is the run's ownership (033/039), carried onto every record this writes so a run never loses
 * the repository or project it was started under.
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
                    // Carry the active project's repo (033) onto the terminal record so the runs list shows it.
                    // `project` likewise: RunRecord.from only knows the descriptor's own `project:` key, so
                    // without this a run of a project whose descriptor omits it would lose its owner the
                    // moment it finished and drop out of the project-scoped runs list (039).
                    val recorded = RunRecord.from(run, Instant.now(), trigger = "manual")
                    recorded.copy(id = id, repo = context.repo, project = recorded.project ?: context.project)
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
                    trigger = "manual",
                    project = context.project,
                    stages = RunRecord.skeleton(pipeline),
                )
            }
            store.record(record)
        }
    }
}
