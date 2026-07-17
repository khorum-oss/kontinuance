package org.khorum.oss.kontinuance.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * A cold [Flow] of run records for live consumers (SSE / WebSocket): an initial snapshot (newest-first,
 * bounded) on subscription, then newly-recorded runs as later polls observe them. This is the live data
 * model the Web UI's "observe" view watches. The `:server` process is separate from the `kontinuance-ci`
 * process that writes runs, so the source of truth is the shared [RunStore] on disk — hence polling; a
 * push backend (or a DB `LISTEN`) can later replace the poll behind this same Flow surface without
 * touching the controllers.
 *
 * Each poll's blocking store read is offloaded with `withContext(Dispatchers.IO)` (consistent with
 * [RunReadFacade], FR-003) and the cadence is a non-blocking `delay`. The flow is cold and structured:
 * it runs only while a subscriber is collecting, so a closed connection cancels the collector and stops
 * the polling — no leaked loops.
 *
 * @param pollIntervalMs how often to re-read the store for new runs.
 * @param snapshotLimit the bound on the initial newest-first snapshot (also the per-poll read bound).
 */
@Component
class RunStream(
    private val store: RunStore,
    @param:Value("\${kontinuance.stream.poll-interval-ms:1000}") private val pollIntervalMs: Long,
    @param:Value("\${kontinuance.stream.snapshot-limit:50}") private val snapshotLimit: Int,
) {

    fun updates(): Flow<RunRecord> = flow {
        val seen = HashSet<String>()
        while (true) {
            // recent() is newest-first; emit the unseen oldest-first so the wire order is chronological.
            val recent = withContext(Dispatchers.IO) { store.recent(snapshotLimit) }
            recent.asReversed().forEach { record ->
                if (seen.add(record.id)) emit(record)
            }
            delay(pollIntervalMs)
        }
    }
}
