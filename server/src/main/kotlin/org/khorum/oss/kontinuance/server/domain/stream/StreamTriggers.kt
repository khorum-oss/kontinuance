package org.khorum.oss.kontinuance.server.domain.stream

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration.Companion.milliseconds

/**
 * The wakeup [Flow] driving a live stream's re-read loop: it emits `Unit` once immediately (for the
 * initial snapshot) and then on every subsequent trigger. A collector re-reads the store on each emission.
 *
 * - [StreamMode.POLL]: a bare timer at [pollIntervalMs].
 * - [StreamMode.PUSH]: the same timer **merged** with [signals], so an in-process change wakes the stream
 *   at once while the timer remains a fallback for out-of-process writes.
 *
 * A single [signals] subscription lives for the whole collection (no per-iteration re-subscribe), so a
 * signal emitted while the previous re-read was in flight is not missed.
 */
fun streamTriggers(mode: StreamMode, pollIntervalMs: Long, signals: Flow<Unit>): Flow<Unit> {
    val ticker = flow {
        while (true) {
            delay(pollIntervalMs.milliseconds)
            emit(Unit)
        }
    }
    val triggers = when (mode) {
        StreamMode.PUSH -> merge(ticker, signals)
        StreamMode.POLL -> ticker
    }
    return triggers.onStart { emit(Unit) }
}
