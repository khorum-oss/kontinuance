package org.khorum.oss.kontinuance.server

import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore

/**
 * The transport-agnostic read handlers over a [RunStore]: health, list runs, get run by id. Each returns
 * a typed value (not JSON) with no dependency on any HTTP server, so the same logic is unit-testable
 * directly and reusable by a future transport (FR-008 / SC-007). The controller serializes the value with
 * Jackson and chooses the status ([getRun] returns `null` for an absent run → the controller maps 404).
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

    /** Health: `{"status":"ok"}`. */
    fun health(): StatusMessage = StatusMessage("ok")

    /** The runs, newest-first, bounded by [limit] (default/clamped). */
    fun listRuns(limit: Int?): RunsResponse = RunsResponse(store.recent(effectiveLimit(limit)))

    /** The run with [id], or `null` when absent (the controller maps that to 404). */
    fun getRun(id: String): RunRecord? = store.get(id)

    private fun effectiveLimit(limit: Int?): Int =
        (limit?.takeIf { it > 0 } ?: defaultLimit).coerceAtMost(maxLimit)

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 500
    }
}
