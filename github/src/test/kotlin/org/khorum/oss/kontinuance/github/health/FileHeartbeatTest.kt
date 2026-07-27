package org.khorum.oss.kontinuance.github.health

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit-tests the file-backed liveness [FileHeartbeat] (036): a beat stamps the clock and increments the
 * cycle count, and [HeartbeatState.read] round-trips it (returning null when absent).
 */
class FileHeartbeatTest {

    @TempDir
    lateinit var dir: Path

    private fun fileIn() = dir.resolve("github-heartbeat.properties")

    @Test
    fun `beat stamps the clock time and starts the cycle count at one`() {
        val file = fileIn()
        FileHeartbeat(file, now = { 1_700_000_000_000 }).beat()

        val state = HeartbeatState.read(file)
        assertEquals(1_700_000_000_000, state?.lastPolledMillis)
        assertEquals(1L, state?.cycles)
    }

    @Test
    fun `repeated beats advance the timestamp and increment the cycle count`() {
        val file = fileIn()
        var clock = 1_000L
        val hb = FileHeartbeat(file) { clock }

        hb.beat()
        clock = 2_000L
        hb.beat()
        clock = 3_000L
        hb.beat()

        val state = HeartbeatState.read(file)
        assertEquals(3_000L, state?.lastPolledMillis)
        assertEquals(3L, state?.cycles)
    }

    @Test
    fun `read of a missing heartbeat file is null`() {
        assertNull(HeartbeatState.read(fileIn()))
    }
}
