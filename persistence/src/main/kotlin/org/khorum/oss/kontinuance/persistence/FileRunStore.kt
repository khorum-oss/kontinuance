package org.khorum.oss.kontinuance.persistence

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Durable [RunStore] writing one JSON file per run under [dir]. Listing is newest-first by file
 * modification time. A malformed file is skipped, never fatal, so one corrupt record cannot break the
 * whole history (FR-007). Sufficient for homelab scale; a database backend can replace it behind
 * [RunStore] later.
 */
class FileRunStore(private val dir: Path) : RunStore {

    init {
        Files.createDirectories(dir)
    }

    override fun record(record: RunRecord) {
        dir.resolve(fileName(record.id)).writeText(record.toJson())
    }

    override fun recent(limit: Int): List<RunRecord> =
        dir.listDirectoryEntries("*.json")
            .sortedByDescending { runCatching { it.getLastModifiedTime().toMillis() }.getOrDefault(0L) }
            .take(limit)
            .mapNotNull { readOrNull(it) }

    override fun get(id: String): RunRecord? =
        dir.resolve(fileName(id)).takeIf { it.exists() }?.let { readOrNull(it) }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun readOrNull(file: Path): RunRecord? =
        try {
            RunRecord.fromJson(file.readText())
        } catch (e: Exception) {
            // FR-007: a corrupt/partial record is skipped so the rest of the history still loads.
            null
        }

    private fun fileName(id: String): String = id.replace(UNSAFE, "_") + ".json"

    private companion object {
        val UNSAFE = Regex("[^A-Za-z0-9_.-]")
    }
}
