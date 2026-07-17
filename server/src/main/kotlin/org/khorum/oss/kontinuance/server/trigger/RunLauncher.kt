package org.khorum.oss.kontinuance.server.trigger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.engine.execution.ApprovalToken
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
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
 */
@Component
class RunLauncher(
    private val store: RunStore,
    private val engine: PipelineEngine,
    private val scope: CoroutineScope,
) {
    fun launch(
        id: String,
        pipeline: Pipeline,
        startedAt: Instant,
        completedStages: List<StageRun> = emptyList(),
    ) {
        scope.launch {
            val record = runCatching {
                withContext(ApprovalToken(id)) {
                    val run = engine.run(pipeline, completedStages = completedStages)
                    RunRecord.from(run, Instant.now(), trigger = "manual").copy(id = id)
                }
            }.getOrElse {
                RunRecord(
                    id = id,
                    pipeline = pipeline.name,
                    status = "Failed",
                    reason = it.message,
                    startedAt = startedAt,
                    endedAt = Instant.now(),
                    trigger = "manual",
                )
            }
            store.record(record)
        }
    }
}
