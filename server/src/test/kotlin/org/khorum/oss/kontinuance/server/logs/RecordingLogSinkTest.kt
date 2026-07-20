package org.khorum.oss.kontinuance.server.logs

import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.persistence.InMemoryRunLogStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit-verifies the per-run [RecordingLogSink] (018): each emitted line is appended to the store under the
 * run id (in order) and also teed to the downstream sink; a second run's sink writes a separate log.
 */
class RecordingLogSinkTest {

    @Test
    fun `appends each line under the run id and tees to the downstream sink`() {
        val store = InMemoryRunLogStore()
        val teed = mutableListOf<String>()
        val sink = RecordingLogSink("run-1", store, tee = LogSink { teed.add(it) })

        sink.emit("[build] compiling")
        sink.emit("[test] 12 passed")

        assertEquals(listOf("[build] compiling", "[test] 12 passed"), store.read("run-1"))
        assertEquals(listOf("[build] compiling", "[test] 12 passed"), teed)
    }

    @Test
    fun `each run records into its own log`() {
        val store = InMemoryRunLogStore()
        RecordingLogSink("run-a", store, tee = LogSink { }).emit("a")
        RecordingLogSink("run-b", store, tee = LogSink { }).emit("b")

        assertEquals(listOf("a"), store.read("run-a"))
        assertEquals(listOf("b"), store.read("run-b"))
    }
}
