package org.khorum.oss.kontinuance.server.domain.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.service.RunChangeNotifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * A cold [Flow] that tails one run's recorded output (023): it emits every line already recorded on
 * subscription, then each new line as later reads observe it, and completes once the run reaches a
 * terminal state (after draining whatever final lines were appended alongside that terminal write). This
 * turns the 018 record-and-poll log into a live tail. The re-read cadence comes from [streamTriggers]: a
 * timer in `poll` mode, or the timer plus in-process [RunChangeNotifier] log-signals in `push` mode (025),
 * so an appended line surfaces with no poll latency while the timer still catches out-of-process writes.
 *
 * Each read's blocking store access is offloaded with `withContext(Dispatchers.IO)`. The flow is cold and
 * structured: it runs only while a subscriber collects, so a closed connection cancels the collector and
 * stops the loop — no leaked work. A run that never reaches a terminal state (an unknown id, or one still
 * running when the client disconnects) keeps tailing until the subscriber goes away.
 *
 * @param pollIntervalMs how often to re-read the log for new lines (the fallback cadence in push mode).
 * @param modeRaw `poll` (default) or `push`; see [StreamMode].
 */
@Component
class RunLogStream(
    private val logs: RunLogStore,
    private val runs: RunStore,
    private val notifier: RunChangeNotifier,
    @param:Value("\${kontinuance.stream.poll-interval-ms:1000}") private val pollIntervalMs: Long,
    @Value("\${kontinuance.stream.mode:poll}") modeRaw: String,
) {

    private val mode = StreamMode.from(modeRaw)

    fun updates(runId: String): Flow<String> = flow {
        var emitted = 0
        // transformWhile drives the same drain-then-stop logic per trigger, completing the flow once the
        // run is terminal (so the SSE controller emits its `end`). We always drain the lines just read
        // before deciding to stop, so the terminal check can never race ahead of a final line.
        emitAll(
            streamTriggers(mode, pollIntervalMs, notifier.logSignals(runId)).transformWhile {
                val recorded = withContext(Dispatchers.IO) { logs.read(runId) }
                val terminal = withContext(Dispatchers.IO) { runs.get(runId) }?.let { isTerminal(it.status) } ?: false
                while (emitted < recorded.size) {
                    emit(recorded[emitted])
                    emitted++
                }
                !terminal
            },
        )
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
