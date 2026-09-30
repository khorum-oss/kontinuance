package org.khorum.oss.kontinuance.server.controller.log

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.server.domain.stream.RunLogStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The SSE tail must keep bytes moving while a run is quiet.
 *
 * Without a heartbeat the stream emits only on new log lines, so a long silent task (`:backend:test` runs
 * for minutes with no output) leaves the connection idle — and Cloudflare terminates a streamed response
 * that transmits nothing for ~100s. Measured on the real gate 2026-09-29: 143s of silence, then
 * `curl: (92) HTTP/2 stream 1 was not closed cleanly: INTERNAL_ERROR (err 2)`. The build was fine; the
 * proxy reaped an idle stream, and the CI job then looked hung for ten minutes.
 *
 * A heartbeat is an SSE **comment** rather than a data event, deliberately: comments carry no `data:`
 * field, so every client — including the CI script's `sed -n 's/^data://p'` filter — ignores them. The
 * connection stays alive and nothing pollutes the log output.
 */
class RunLogStreamControllerTest {

    private fun streamOf(lines: Flow<String>): RunLogStream =
        mockk<RunLogStream>().also { every { it.updates(any()) } returns lines }

    /** A flow that produces nothing and never completes — a run mid-way through a silent task. */
    private fun silentForever(): Flow<String> = flow { kotlinx.coroutines.awaitCancellation() }

    @Test
    fun `emits a heartbeat comment while the run produces no output`() = runTest {
        val controller = RunLogStreamController(streamOf(silentForever()), heartbeatMs = 50)

        val events = controller.stream("run-1").take(2).toList()

        assertEquals(2, events.size, "a silent run must still produce traffic")
        assertTrue(events.all { it.comment() != null }, "heartbeats are comments: $events")
        assertTrue(events.all { it.data() == null }, "a heartbeat must carry no data payload")
    }

    @Test
    fun `log lines are data events, not comments`() = runTest {
        val controller = RunLogStreamController(
            streamOf(flow { emit("[build] compiling"); kotlinx.coroutines.awaitCancellation() }),
            heartbeatMs = 50,
        )

        val first = controller.stream("run-1").take(1).toList().single()

        assertEquals("[build] compiling", first.data())
        assertEquals("log", first.event())
        assertNull(first.comment())
    }

    @Test
    fun `a terminal run still ends, and the heartbeat does not hold the stream open`() = runTest {
        // An empty, completing flow is a run that has finished: the tail must drain and emit `end`.
        // If the heartbeat were merged naively it would never complete and the client would hang forever.
        val controller = RunLogStreamController(streamOf(emptyFlow()), heartbeatMs = 50)

        val events = controller.stream("run-1").toList()

        val end = events.lastOrNull { it.event() == "end" }
        assertNotNull(end, "a completing log flow must still yield the terminal end event: $events")
    }
}
