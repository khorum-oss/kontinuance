package org.khorum.oss.kontinuance.server.pipeline

import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.persistence.StepRecord
import org.khorum.oss.kontinuance.server.stub.StubFixtures
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Serves `/api/runs/{id}/pipeline` from the run's **real persisted stage/step breakdown** (recorded in
 * [RunRecord] with per-step status, timing, and tool), mapped into the pipeline contract shape. Falls
 * back to fixture data when the run is unknown or predates stage recording (older records carry no
 * stages). Dependencies between tasks are not modeled by the engine (stages run in order, steps within a
 * stage in order), so `deps` is empty — the shape stays stable for a future DAG.
 */
@RestController
class PipelineController(private val store: RunStore) {

    @GetMapping("/api/runs/{id}/pipeline")
    fun pipeline(@PathVariable id: String): PipelineResponse {
        val run = store.get(id)
        return if (run != null && run.stages.isNotEmpty()) render(run) else StubFixtures.pipeline(id)
    }

    private fun render(run: RunRecord): PipelineResponse = PipelineResponse(
        runId = run.id,
        stages = run.stages.mapIndexed { i, stage ->
            PipelineStage(
                id = "s${i + 1}",
                name = stage.name,
                tasks = stage.steps.map { task(it) },
            )
        },
    )

    private fun task(step: StepRecord): PipelineTask = PipelineTask(
        id = step.name,
        name = step.name,
        tool = step.tool ?: "run",
        status = statusOf(step.status),
        progress = progressOf(step.status),
    )

    private companion object {
        const val FULL = 100
        const val MID = 50

        fun statusOf(raw: String): String = when {
            raw.startsWith("Success", true) -> "success"
            raw.startsWith("Fail", true) || raw.startsWith("Timed", true) -> "failed"
            raw.startsWith("Run", true) -> "running"
            raw.startsWith("Skip", true) -> "skipped"
            else -> "pending"
        }

        fun progressOf(raw: String): Int = when (statusOf(raw)) {
            "success", "failed", "skipped" -> FULL
            "running" -> MID
            else -> 0
        }
    }
}
