package org.khorum.oss.kontinuance.server

import org.khorum.oss.kontinuance.persistence.RunStore

/**
 * The transport-agnostic read handlers over a [RunStore]: health, list runs, get run by id. Each
 * returns an [ApiResponse] (status + JSON) with no dependency on any HTTP server, so the same logic is
 * unit-testable directly and reusable by a future transport (FR-008 / SC-007).
 *
 * @param store the run history to read.
 * @param defaultLimit applied when the caller supplies no/invalid limit.
 * @param maxLimit hard cap so a list response is never unbounded (FR-002).
 */
class RunApi(
    private val store: RunStore,
    private val defaultLimit: Int = DEFAULT_LIMIT,
    private val maxLimit: Int = MAX_LIMIT,
) {

    /** `200 {"status":"ok"}`. */
    fun health(): ApiResponse = ApiResponse(OK, JsonView.message("status", "ok"))

    /** `200 {"runs":[…]}` newest-first, bounded by [limit] (default/clamped). */
    fun listRuns(limit: Int?): ApiResponse =
        ApiResponse(OK, JsonView.runs(store.recent(effectiveLimit(limit))))

    /** `200 <run>` or `404 {"error":"not found"}`. */
    fun getRun(id: String): ApiResponse {
        val record = store.get(id) ?: return ApiResponse(NOT_FOUND, JsonView.message("error", "not found"))
        return ApiResponse(OK, JsonView.run(record))
    }

    private fun effectiveLimit(limit: Int?): Int =
        (limit?.takeIf { it > 0 } ?: defaultLimit).coerceAtMost(maxLimit)

    private companion object {
        const val OK = 200
        const val NOT_FOUND = 404
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 500
    }
}
