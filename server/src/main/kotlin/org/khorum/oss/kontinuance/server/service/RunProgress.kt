package org.khorum.oss.kontinuance.server.service

import org.khorum.oss.kontinuance.engine.execution.StatusEvent
import org.khorum.oss.kontinuance.engine.execution.Target
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.StageRecord
import org.khorum.oss.kontinuance.persistence.StepRecord
import java.time.Instant

/**
 * A running pipeline's live stage/step breakdown, advanced by the engine's [StatusEvent]s (042).
 *
 * The engine reports every transition as it happens, but until this existed nothing consumed them: a
 * run's stages were persisted twice — the declared shape when it started, the executed one when it
 * finished — so for the whole duration of a build the pipeline view showed every step `Pending` at 0%
 * and then jumped straight to done. A running pipeline never looked like it was running.
 *
 * Starts from the pipeline as declared (so the shape is right before anything executes) and applies each
 * event to it, stamping a step's start and end as it passes through them. Not thread-safe: it belongs to
 * the single coroutine collecting one run's events.
 */
class RunProgress(pipeline: Pipeline, private val now: () -> Instant = Instant::now) {

    private val stages: MutableList<MutableStage> = pipeline.stages.map { stage ->
        MutableStage(
            name = stage.name,
            steps = stage.steps.map { step ->
                MutableStep(name = step.name, tool = step.definition::class.simpleName?.removeSuffix("Step")?.lowercase())
            }.toMutableList(),
        )
    }.toMutableList()

    /**
     * Applies [event], returning true when it changed anything.
     *
     * A false return means there is nothing new to persist — the run store is the only consumer, and
     * rewriting an identical record would wake every SSE client for no reason. Events for a stage or step
     * the declared pipeline does not contain are ignored rather than invented: the shape a run shows must
     * stay the shape it was launched with.
     */
    fun apply(event: StatusEvent): Boolean {
        val status = event.status::class.simpleName ?: return false
        return when (val target = event.target) {
            is Target.StepTarget -> step(target.stage, target.step)?.advance(status) ?: false
            is Target.StageTarget -> stage(target.stage)?.advance(status) ?: false
            // The overall pipeline status is the run record's own `status`, which the trigger and the
            // terminal record own; nothing here to advance.
            is Target.PipelineTarget -> false
        }
    }

    /** The breakdown as it stands, in the shape [RunRecord] persists. */
    fun snapshot(): List<StageRecord> = stages.map { stage ->
        StageRecord(
            name = stage.name,
            status = stage.status,
            steps = stage.steps.map { StepRecord(it.name, it.status, it.tool, it.startedAt, it.endedAt) },
        )
    }

    private fun stage(name: String) = stages.firstOrNull { it.name == name }

    private fun step(stageName: String, stepName: String) =
        stage(stageName)?.steps?.firstOrNull { it.name == stepName }

    private class MutableStage(val name: String, val steps: MutableList<MutableStep>) {
        var status: String = PENDING

        fun advance(next: String): Boolean {
            if (status == next) return false
            status = next
            return true
        }
    }

    private class MutableStep(val name: String, val tool: String?) {
        var status: String = PENDING
        var startedAt: Instant? = null
        var endedAt: Instant? = null

        fun advanceAt(next: String, at: Instant): Boolean {
            if (status == next) return false
            status = next
            if (next == RUNNING) startedAt = at else if (startedAt != null || next != PENDING) endedAt = at
            return true
        }
    }

    private fun MutableStep.advance(next: String) = advanceAt(next, now())

    private companion object {
        const val PENDING = "Pending"
        const val RUNNING = "Running"
    }
}
