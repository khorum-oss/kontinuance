package org.khorum.oss.kontinuance.engine.execution

import org.khorum.oss.kontinuance.dsl.model.PipelineStatus

/** Identifies the unit a [StatusEvent] is about. */
sealed interface Target {
    /** The pipeline as a whole. */
    data class PipelineTarget(val pipeline: String) : Target

    /** A stage within a pipeline. */
    data class StageTarget(val pipeline: String, val stage: String) : Target

    /** A step within a stage. */
    data class StepTarget(val pipeline: String, val stage: String, val step: String) : Target
}

/** A single lifecycle transition observed during a run. */
data class StatusEvent(val target: Target, val status: PipelineStatus)
