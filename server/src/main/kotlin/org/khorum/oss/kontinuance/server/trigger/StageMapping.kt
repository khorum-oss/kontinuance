package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.engine.model.StepRun
import org.khorum.oss.kontinuance.persistence.StageRecord
import org.khorum.oss.kontinuance.persistence.StepRecord

/**
 * Reconstructs an engine [StageRun] from a persisted [StageRecord] so a paused run can be resumed by
 * handing its completed stages back to the engine (which skips and reuses them). Only status + timing
 * are restored — never logs — matching what the record persists.
 */
internal fun StageRecord.toStageRun(): StageRun =
    StageRun(name = name, status = statusOf(status), stepRuns = steps.map { it.toStepRun() })

private fun StepRecord.toStepRun(): StepRun =
    StepRun(name = name, status = statusOf(status), exitCode = null, startedAt = startedAt, endedAt = endedAt)

/** Maps a persisted status name back to a [PipelineStatus] (the inverse of `status::class.simpleName`). */
private fun statusOf(name: String): PipelineStatus = when (name) {
    "Success" -> PipelineStatus.Success
    "Skipped" -> PipelineStatus.Skipped
    "Running" -> PipelineStatus.Running
    "Queued" -> PipelineStatus.Queued
    "Cancelled" -> PipelineStatus.Cancelled
    "TimedOut" -> PipelineStatus.TimedOut
    "WaitingOnApproval" -> PipelineStatus.WaitingOnApproval
    else -> PipelineStatus.Pending
}
