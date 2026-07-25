package org.khorum.oss.kontinuance.server.logs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * A cold [Flow] that tails one run's recorded output (023): it emits every line already recorded on
 * subscription, then each new line as later polls observe it, and completes once the run reaches a
 * terminal state (after draining whatever final lines were appended alongside that terminal write). This
 * turns the 018 record-and-poll log into a live tail — the same shared-store polling model as [RunStream]
 * (the `:server` process is separate from the writer, so the source of truth is the store on disk), so a
 * push backend can later replace the poll behind this same Flow surface without touching the controller.
 *
 * Each poll's blocking store read is offloaded with `withContext(Dispatchers.IO)` and the cadence is a
 * non-blocking `delay`. The flow is cold and structured: it runs only while a subscriber collects, so a
 * closed connection cancels the collector and stops the polling — no leaked loops. A run that never
 * reaches a terminal state (e.g. an unknown id, or one still running when the client disconnects) simply
 * keeps tailing until the subscriber goes away.
 *
 * @param pollIntervalMs how often to re-read the log for new lines.
 */
@Component
class RunLogStream(
    private val logs: RunLogStore,
    private val runs: RunStore,
    @param:Value("\${kontinuance.stream.poll-interval-ms:1000}") private val pollIntervalMs: Long,
) {

    fun updates(runId: String): Flow<String> = flow {
        var emitted = 0
        while (true) {
            val recorded = withContext(Dispatchers.IO) { logs.read(runId) }
            // Read the run's status in the same tick as the lines so the terminal check can never race
            // ahead of a final line: we always drain the lines we just read before deciding to stop.
            val terminal = withContext(Dispatchers.IO) { runs.get(runId) }?.let { isTerminal(it.status) } ?: false
            while (emitted < recorded.size) {
                emit(recorded[emitted])
                emitted++
            }
            if (terminal) break
            delay(pollIntervalMs)
        }
    }

    private fun isTerminal(status: String): Boolean {
        val s = status.lowercase()
        return s.startsWith("success") ||
            s.startsWith("fail") ||
            s.startsWith("cancel") ||
            s.startsWith("timed") ||
            s.startsWith("skip")
    }
}
