package org.khorum.oss.kontinuance.server.stream

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Component

/**
 * An in-process bus of "something changed" signals for the live streams (025). Writers signal after a
 * store write; the streams, in [StreamMode.PUSH], collect these to wake immediately instead of waiting for
 * the next poll. Signals carry no data — a woken stream re-reads the store (the source of truth), so the
 * delta logic is identical whether the wakeup came from a signal or the poll timer, and coalesced signals
 * are harmless.
 *
 * Backed by [MutableSharedFlow] with no replay (a signal only matters to collectors already listening — a
 * fresh subscriber reads the current store state on subscription) and a small drop-oldest buffer so a
 * burst of writes never suspends a writer. Signalling from a non-streaming context is fire-and-forget via
 * `tryEmit`. Only meaningful when the writer and the stream share this process; cross-process writes are
 * caught by the poll fallback, never by these signals.
 */
@Component
class RunChangeNotifier {

    private val runs = MutableSharedFlow<Unit>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val logs = MutableSharedFlow<String>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Signals that the run history changed (a run was recorded or its status updated). */
    fun signalRuns() {
        runs.tryEmit(Unit)
    }

    /** Signals that [runId]'s log gained a line. */
    fun signalLog(runId: String) {
        logs.tryEmit(runId)
    }

    /** Wakeups for the runs-list stream. */
    fun runSignals(): Flow<Unit> = runs.asSharedFlow()

    /** Wakeups for [runId]'s log tail (only signals for that run). */
    fun logSignals(runId: String): Flow<Unit> = logs.asSharedFlow().filter { it == runId }.map { }

    private companion object {
        const val BUFFER = 64
    }
}
