package org.khorum.oss.kontinuance.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Run
import java.time.Instant

/**
 * The persisted summary of one pipeline run — metadata and status only, never step logs or secret
 * values (FR-009; the engine guarantees `Failed.reason` carries no unmasked secrets). Optional CI
 * context (repo/sha/trigger) is present for runs started by the event source. Serialized as flat JSON
 * via the runtime API (no compiler plugin, no new dependency).
 */
data class RunRecord(
    val id: String,
    val pipeline: String,
    val status: String,
    val failingStep: String? = null,
    val reason: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val repo: String? = null,
    val sha: String? = null,
    val trigger: String? = null,
) {

    /** Flat JSON for on-disk storage. */
    fun toJson(): String = buildJsonObject {
        put("id", id)
        put("pipeline", pipeline)
        put("status", status)
        failingStep?.let { put("failingStep", it) }
        reason?.let { put("reason", it) }
        startedAt?.let { put("startedAt", it.toString()) }
        endedAt?.let { put("endedAt", it.toString()) }
        repo?.let { put("repo", it) }
        sha?.let { put("sha", it) }
        trigger?.let { put("trigger", it) }
    }.toString()

    companion object {
        /**
         * Summarizes [run] into a record. [endedAt] defaults to the latest step end (or now, supplied by
         * the caller as [recordedAt]); CI context is optional.
         */
        fun from(
            run: Run,
            recordedAt: Instant,
            repo: String? = null,
            sha: String? = null,
            trigger: String? = null,
        ): RunRecord {
            val steps = run.stageRuns.flatMap { it.stepRuns }
            val failed = run.status as? PipelineStatus.Failed
            return RunRecord(
                id = run.id.value,
                pipeline = run.pipeline.name,
                status = run.status::class.simpleName ?: "Unknown",
                failingStep = failed?.step,
                reason = failed?.reason,
                startedAt = steps.mapNotNull { it.startedAt }.minOrNull(),
                endedAt = steps.mapNotNull { it.endedAt }.maxOrNull() ?: recordedAt,
                repo = repo,
                sha = sha,
                trigger = trigger,
            )
        }

        /** Parses a record from [json] (as written by [toJson]). */
        fun fromJson(json: String): RunRecord {
            val obj = Json.parseToJsonElement(json).jsonObject
            fun str(key: String): String? = obj[key]?.jsonPrimitive?.content
            return RunRecord(
                id = requireNotNull(str("id")) { "run record is missing 'id'" },
                pipeline = str("pipeline").orEmpty(),
                status = str("status") ?: "Unknown",
                failingStep = str("failingStep"),
                reason = str("reason"),
                startedAt = str("startedAt")?.let(Instant::parse),
                endedAt = str("endedAt")?.let(Instant::parse),
                repo = str("repo"),
                sha = str("sha"),
                trigger = str("trigger"),
            )
        }
    }
}
