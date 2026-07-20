package org.khorum.oss.kontinuance.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.khorum.oss.kontinuance.persistence.RunRecord

/**
 * Serializes API values to JSON with the runtime `kotlinx-serialization-json` (no compiler plugin, no
 * new dependency). A single run reuses [RunRecord.toJson]; a list wraps them under `runs`.
 */
internal object JsonView {

    /** A run list: `{"runs":[ <run>… ]}`. */
    fun runs(records: List<RunRecord>): String {
        val elements = records.joinToString(",") { it.toJson() }
        return """{"runs":[$elements]}"""
    }

    /** A single run object (the record's own JSON). */
    fun run(record: RunRecord): String = record.toJson()

    /** A run's recorded log: `{"runId":"…","lines":[ "<line>"… ]}` (lines are already masked). */
    fun runLogs(runId: String, lines: List<String>): String =
        buildJsonObject {
            put("runId", runId)
            putJsonArray("lines") { lines.forEach { add(it) } }
        }.toString()

    /** A simple `{"<key>":"<value>"}` message (health, errors). */
    fun message(key: String, value: String): String =
        buildJsonObject { put(key, value) }.toString()

    /** True if [text] parses as a JSON object — used by tests/callers to validate output. */
    fun isJsonObject(text: String): Boolean =
        runCatching { Json.parseToJsonElement(text).jsonObject }.isSuccess
}
