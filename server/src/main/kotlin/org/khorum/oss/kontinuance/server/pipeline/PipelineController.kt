package org.khorum.oss.kontinuance.server.pipeline

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.persistence.StepRecord
import org.khorum.oss.kontinuance.server.stub.StubFixtures
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
    fun pipeline(@PathVariable id: String): ResponseEntity<ByteArray> {
        val run = store.get(id)
        val body = if (run != null && run.stages.isNotEmpty()) render(run) else StubFixtures.pipeline(id)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body.toByteArray())
    }

    private fun render(run: RunRecord): String = buildJsonObject {
        put("runId", run.id)
        putJsonArray("stages") {
            run.stages.forEachIndexed { i, stage ->
                addJsonObject {
                    put("id", "s${i + 1}")
                    put("name", stage.name)
                    putJsonArray("tasks") { stage.steps.forEach { addTask(it) } }
                }
            }
        }
    }.toString()

    private fun kotlinx.serialization.json.JsonArrayBuilder.addTask(step: StepRecord) = addJsonObject {
        put("id", step.name)
        put("name", step.name)
        put("tool", step.tool ?: "run")
        put("status", statusOf(step.status))
        put("progress", progressOf(step.status))
        putJsonArray("deps") { /* engine models order, not a DAG */ }
    }

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
