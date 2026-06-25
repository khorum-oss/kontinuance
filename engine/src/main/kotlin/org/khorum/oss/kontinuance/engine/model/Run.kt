package org.khorum.oss.kontinuance.engine.model

import java.time.Instant

/** Unique identifier for one execution of a [Pipeline]. */
@JvmInline
value class RunId(val value: String)

/**
 * One execution of a [Pipeline].
 *
 * @param id unique per execution.
 * @param pipeline the definition that was executed.
 * @param status the overall status, derived from the stage/step statuses.
 * @param stageRuns per-stage execution state, in declared order.
 */
data class Run(
    val id: RunId,
    val pipeline: Pipeline,
    val status: PipelineStatus,
    val stageRuns: List<StageRun>,
)

/**
 * Execution state of a single [Stage] within a [Run].
 *
 * @param name the stage name.
 * @param status the stage's terminal status (derived from its steps).
 * @param stepRuns per-step execution state, in declared order.
 */
data class StageRun(
    val name: String,
    val status: PipelineStatus,
    val stepRuns: List<StepRun>,
)

/**
 * Execution state of a single [Step] within a [StageRun].
 *
 * @param name the step name.
 * @param status the step's terminal status.
 * @param exitCode the process exit code for command steps, or `null` when not applicable
 *   (e.g. skipped, timed out, or failed to launch).
 * @param startedAt when execution began, or `null` if it never started (e.g. skipped).
 * @param endedAt when execution reached a terminal status, or `null` if not started.
 */
data class StepRun(
    val name: String,
    val status: PipelineStatus,
    val exitCode: Int? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
)
