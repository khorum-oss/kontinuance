package org.khorum.oss.kontinuance.server.controller

import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.persistence.StepRecord
import org.khorum.oss.kontinuance.server.domain.ErrorResponse
import org.khorum.oss.kontinuance.server.domain.PipelineResponse
import org.khorum.oss.kontinuance.server.domain.PipelineStage
import org.khorum.oss.kontinuance.server.domain.PipelineTask
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Serves `/api/runs/{id}/pipeline` from the run's **real persisted stage/step breakdown** (recorded in
 * [RunRecord] with per-step status, timing, and tool), mapped into the pipeline contract shape. It answers
 * only for the run asked about: an unknown id is a `404`, and a known run that recorded no stages (one
 * predating stage recording) answers with an empty stage list. Neither case invents a pipeline — serving
 * a fixture here showed a six-stage demo flow that belonged to no run at all, which reads as the run's
 * own pipeline and contradicts everything else on screen. Dependencies between tasks are not modeled by
 * the engine (stages run in order, steps within a stage in order), so `deps` is empty — the shape stays
 * stable for a future DAG.
 */
@RestController
class PipelineController(private val store: RunStore) {

    @GetMapping("/api/runs/{id}/pipeline")
    fun pipeline(@PathVariable id: String): ResponseEntity<*> {
        val run = store.get(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("not found"))
        return ResponseEntity.ok(render(run))
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
