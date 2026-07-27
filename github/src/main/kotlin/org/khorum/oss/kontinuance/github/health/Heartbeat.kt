package org.khorum.oss.kontinuance.github.health

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * A liveness signal (036): the event source records a heartbeat after each **successful** poll so an
 * operator (via the server's `/api/source`) can tell it is alive and when it last checked GitHub. Kept
 * deliberately small — a properties file beside the poll cursor — mirroring [org.khorum.oss.kontinuance
 * .github.poll.CursorStore]; the persistence feature can replace it behind this seam.
 */
fun interface Heartbeat {
    /** Records that a poll cycle just completed successfully. */
    fun beat()
}

/** A heartbeat that records nothing — the default, so a poller without one still runs (server shows unknown). */
object NoOpHeartbeat : Heartbeat {
    override fun beat() = Unit
}

/** The last recorded heartbeat: when the poller last checked GitHub, and how many cycles it has completed. */
data class HeartbeatState(val lastPolledMillis: Long, val cycles: Long) {
    companion object {
        private const val LAST_POLLED = "lastPolledMillis"
        private const val CYCLES = "cycles"

        /** Reads the heartbeat at [file], or `null` when it is absent or unreadable. */
        fun read(file: Path): HeartbeatState? {
            if (!Files.isRegularFile(file)) return null
            val props = Properties()
            return runCatching {
                Files.newInputStream(file).use { props.load(it) }
                val last = props.getProperty(LAST_POLLED)?.toLongOrNull() ?: return null
                HeartbeatState(last, props.getProperty(CYCLES)?.toLongOrNull() ?: 0L)
            }.getOrNull()
        }

        internal fun write(file: Path, state: HeartbeatState) {
            val props = Properties().apply {
                setProperty(LAST_POLLED, state.lastPolledMillis.toString())
                setProperty(CYCLES, state.cycles.toString())
            }
            file.parent?.let { Files.createDirectories(it) }
            Files.newOutputStream(file).use { props.store(it, "kontinuance github event-source heartbeat") }
        }
    }
}

/**
 * File-backed [Heartbeat] (a properties file). Each [beat] stamps the current time (from [now]) and
 * increments the cycle count, so the server can show "last checked N ago · M cycles".
 */
class FileHeartbeat(
    private val file: Path,
    private val now: () -> Long = System::currentTimeMillis,
) : Heartbeat {
    override fun beat() {
        val cycles = (HeartbeatState.read(file)?.cycles ?: 0L) + 1
        HeartbeatState.write(file, HeartbeatState(lastPolledMillis = now(), cycles = cycles))
    }
}
