package org.khorum.oss.kontinuance.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Durable, append-only store of a run's line-oriented output (018). Lines are recorded exactly as the
 * engine's masking sink emits them (already redacted, `[step] `-prefixed) and read back in order by run id.
 * Small on purpose — the file default can be replaced by a database backend behind this interface without
 * changing callers, mirroring [RunStore].
 */
interface RunLogStore {
    /** Append one already-masked line to [runId]'s log. */
    fun append(runId: String, line: String)

    /** The lines recorded for [runId], in order; empty when none (including an unknown id). */
    fun read(runId: String): List<String>
}

/** A store that discards everything — the "no log store configured" sink so callers need no null checks. */
object NoOpRunLogStore : RunLogStore {
    override fun append(runId: String, line: String) = Unit
    override fun read(runId: String): List<String> = emptyList()
}

/** In-memory [RunLogStore] for tests and non-durable use; per-id isolation, insertion order preserved. */
class InMemoryRunLogStore : RunLogStore {
    private val logs = ConcurrentHashMap<String, MutableList<String>>()

    override fun append(runId: String, line: String) {
        val lines = logs.getOrPut(runId) { mutableListOf() }
        synchronized(lines) { lines.add(line) }
    }

    override fun read(runId: String): List<String> {
        val lines = logs[runId] ?: return emptyList()
        return synchronized(lines) { lines.toList() }
    }
}

/**
 * Durable [RunLogStore] appending one line per row to `<dir>/<sanitised-id>.log`. Appends are serialised per
 * run id so concurrent steps of one run never interleave mid-line. Sufficient for homelab scale.
 */
class FileRunLogStore(private val dir: Path) : RunLogStore {

    private val locks = ConcurrentHashMap<String, Any>()

    init {
        Files.createDirectories(dir)
    }

    override fun append(runId: String, line: String) {
        val lock = locks.getOrPut(runId) { Any() }
        synchronized(lock) {
            Files.write(
                dir.resolve(fileName(runId)),
                listOf(line),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    override fun read(runId: String): List<String> {
        val file = dir.resolve(fileName(runId))
        return if (file.exists()) file.readLines() else emptyList()
    }

    private fun fileName(id: String): String = id.replace(UNSAFE, "_") + ".log"

    private companion object {
        val UNSAFE = Regex("[^A-Za-z0-9_.-]")
    }
}
