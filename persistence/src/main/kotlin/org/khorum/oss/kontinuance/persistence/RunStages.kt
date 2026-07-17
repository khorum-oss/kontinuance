package org.khorum.oss.kontinuance.persistence

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant

/**
 * The persisted per-step breakdown of a run — status + timing only, never step logs or secret values
 * (FR-009). Captured alongside [RunRecord] so the UI can render the actual stage/task flow of a run
 * (`/api/runs/{id}/pipeline`) instead of fixture data. `tool` is the step's kind (run/gradle/docker/npm),
 * derived from the pipeline definition when recording.
 */
data class StepRecord(
    val name: String,
    val status: String,
    val tool: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
)

data class StageRecord(
    val name: String,
    val status: String,
    val steps: List<StepRecord> = emptyList(),
)

/** Appends a `stages` array to the record JSON (omitted when empty, for backward compatibility). */
internal fun JsonObjectBuilder.putStages(stages: List<StageRecord>) {
    if (stages.isEmpty()) return
    putJsonArray("stages") {
        stages.forEach { stage ->
            addJsonObject {
                put("name", stage.name)
                put("status", stage.status)
                putJsonArray("steps") { stage.steps.forEach { addStep(it) } }
            }
        }
    }
}

private fun kotlinx.serialization.json.JsonArrayBuilder.addStep(step: StepRecord) = addJsonObject {
    put("name", step.name)
    put("status", step.status)
    step.tool?.let { put("tool", it) }
    step.startedAt?.let { put("startedAt", it.toString()) }
    step.endedAt?.let { put("endedAt", it.toString()) }
}

/** Parses the optional `stages` array from a record JSON object; empty when absent (older records). */
internal fun parseStages(obj: JsonObject): List<StageRecord> =
    obj["stages"]?.jsonArray?.map { it.jsonObject.toStage() } ?: emptyList()

private fun JsonObject.toStage(): StageRecord = StageRecord(
    name = str("name"),
    status = str("status"),
    steps = this["steps"]?.jsonArray?.map { it.jsonObject.toStep() } ?: emptyList(),
)

private fun JsonObject.toStep(): StepRecord = StepRecord(
    name = str("name"),
    status = str("status"),
    tool = strOrNull("tool"),
    startedAt = strOrNull("startedAt")?.let(Instant::parse),
    endedAt = strOrNull("endedAt")?.let(Instant::parse),
)

private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()
private fun JsonObject.strOrNull(key: String): String? = this[key]?.jsonPrimitive?.content
