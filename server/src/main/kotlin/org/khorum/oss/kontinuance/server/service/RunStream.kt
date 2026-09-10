package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.domain.stream.StreamMode
import org.khorum.oss.kontinuance.server.domain.stream.streamTriggers
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
 * Each read's blocking store access is offloaded with `withContext(Dispatchers.IO)` (consistent with
 * [RunReadFacade], FR-003). The re-read cadence comes from [streamTriggers]: a timer in `poll` mode, or the
 * timer plus in-process [RunChangeNotifier] signals in `push` mode (025), so a server-triggered run appears
 * with no poll latency while the timer still catches out-of-process writes. The flow is cold and
 * structured: it runs only while a subscriber is collecting, so a closed connection cancels the collector
 * and stops the loop — no leaked work.
 *
 * @param pollIntervalMs how often to re-read the store for new runs (the fallback cadence in push mode).
 * @param snapshotLimit the bound on the initial newest-first snapshot (also the per-read bound).
 * @param modeRaw `poll` (default) or `push`; see [StreamMode].
 */
@Component
class RunStream(
    private val store: RunStore,
    private val notifier: RunChangeNotifier,
    @param:Value("\${kontinuance.stream.poll-interval-ms:1000}") private val pollIntervalMs: Long,
    @param:Value("\${kontinuance.stream.snapshot-limit:50}") private val snapshotLimit: Int,
    @Value("\${kontinuance.stream.mode:poll}") modeRaw: String,
) {

    private val mode = StreamMode.from(modeRaw)

    fun updates(): Flow<RunRecord> = flow {
        // Keyed on the record, not merely its id: a run is not a one-shot announcement. Its steps advance
        // while its `status` stays `Running` from the first to the last, so a stream that speaks once per
        // id leaves every live consumer showing the run as it looked the moment it started — a progress
        // view frozen at zero for the whole build. RunRecord is a data class, so structural equality is
        // the change test; consumers merge by id, so a re-emitted run replaces its predecessor.
        val seen = HashMap<String, RunRecord>()
        streamTriggers(mode, pollIntervalMs, notifier.runSignals()).collect {
            // recent() is newest-first; emit changed records oldest-first so the wire order is chronological.
            val recent = withContext(Dispatchers.IO) { store.recent(snapshotLimit) }
            recent.asReversed().forEach { record ->
                if (seen.put(record.id, record) != record) emit(record)
            }
        }
    }
}
