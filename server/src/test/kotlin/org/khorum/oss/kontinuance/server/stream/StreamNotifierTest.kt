package org.khorum.oss.kontinuance.server.stream

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunLogStore
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import kotlin.test.assertEquals

/**
 * Unit-level checks for the 025 push seam: [StreamMode] parsing, the [RunChangeNotifier] signal bus, and
 * the notifying store decorators. End-to-end push delivery through the SSE endpoints is covered by the
 * push ITs.
 */
class StreamNotifierTest {

    @Test
    fun `stream mode parses push and defaults everything else to poll`() {
        assertEquals(StreamMode.PUSH, StreamMode.from("push"))
        assertEquals(StreamMode.PUSH, StreamMode.from("  PUSH "))
        assertEquals(StreamMode.POLL, StreamMode.from("poll"))
        assertEquals(StreamMode.POLL, StreamMode.from(null))
        assertEquals(StreamMode.POLL, StreamMode.from("something-else"))
    }

    @Test
    fun `signalRuns wakes a run-signal collector`() = runBlocking {
        val notifier = RunChangeNotifier()
        val received = async { withTimeout(TIMEOUT_MS) { notifier.runSignals().first() } }
        // The collector may not have subscribed yet (no replay); signal until it is delivered.
        while (received.isActive) {
            notifier.signalRuns()
            delay(POLL_MS)
        }
        received.await() // no timeout thrown → the signal was delivered
    }

    @Test
    fun `logSignals only wakes for the matching run id`() = runBlocking {
        val notifier = RunChangeNotifier()
        val received = async { withTimeout(TIMEOUT_MS) { notifier.logSignals("run-1").first() } }
        while (received.isActive) {
            notifier.signalLog("other-run") // must be ignored by the run-1 collector
            notifier.signalLog("run-1")
            delay(POLL_MS)
        }
        received.await()
    }

    @Test
    fun `the notifying run store delegates reads and writes`() {
        val inner = InMemoryRunStore()
        val store = NotifyingRunStore(inner, RunChangeNotifier())

        store.record(RunRecord(id = "a", pipeline = "p", status = "Running"))

        assertEquals("a", store.get("a")?.id)
        assertEquals(listOf("a"), store.recent(10).map { it.id })
        assertEquals("a", inner.get("a")?.id) // the write reached the delegate
    }

    @Test
    fun `the notifying log store delegates reads and writes`() {
        val inner = InMemoryRunLogStore()
        val store = NotifyingRunLogStore(inner, RunChangeNotifier())

        store.append("run-1", "[build] hi")

        assertEquals(listOf("[build] hi"), store.read("run-1"))
        assertEquals(listOf("[build] hi"), inner.read("run-1")) // the write reached the delegate
    }

    private companion object {
        const val TIMEOUT_MS = 3000L
        const val POLL_MS = 20L
    }
}
