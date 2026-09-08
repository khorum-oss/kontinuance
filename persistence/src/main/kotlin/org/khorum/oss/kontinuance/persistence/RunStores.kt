package org.khorum.oss.kontinuance.persistence

import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Opens the run history for a state directory, choosing between the two backends (041).
 *
 * Everything that persists run state — the server and the standalone `kontinuance-ci` CLI — opens it
 * here rather than naming a backend itself, so the two can never end up reading different stores of the
 * same directory. Whichever backend is chosen, the layout under [dir] is the same one the deployment
 * already mounts; nothing new has to be provisioned.
 */
object RunStores {

    /** The database file, when the SQLite backend is in use. Named so `sqlite3` on it is obvious. */
    const val DATABASE_FILE = "kontinuance.db"

    /** The subdirectory of per-run log files the file backend writes. */
    const val LOG_DIR = "logs"

    /** Which durable implementation backs the stores. */
    enum class Backend {
        /** One JSON file per run plus one log file per run — the original layout (006/018). */
        FILE,

        /** An embedded SQLite database, `kontinuance.db`, holding both (041). */
        SQLITE,
        ;

        companion object {
            /**
             * The backend named by [value], case-insensitively; [SQLITE] when unset or blank.
             *
             * An unrecognised name is an error rather than a silent fall back to the default: a typo in
             * `KONTINUANCE_STORE_BACKEND` would otherwise start the server against a store the operator
             * did not ask for, and they would find out from missing history rather than from a message.
             */
            fun of(value: String?): Backend {
                val name = value?.trim().orEmpty()
                if (name.isEmpty()) return SQLITE
                return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: error("unknown run store backend '$name' — use one of ${entries.joinToString { it.name.lowercase() }}")
            }
        }
    }

    /** Both stores over [dir], on [backend], already migrated and ready to use. */
    fun open(dir: Path, backend: Backend = Backend.SQLITE): Stores = when (backend) {
        Backend.FILE -> Stores(FileRunStore(dir), FileRunLogStore(dir.resolve(LOG_DIR)))
        Backend.SQLITE -> {
            val database = dir.resolve(DATABASE_FILE)
            val stores = Stores(SqliteRunStore(database), SqliteRunLogStore(database))
            importFromFiles(dir, stores)
            stores
        }
    }

    /** The pair of stores over one directory, so callers open the backend once and get both. */
    data class Stores(val runs: RunStore, val logs: RunLogStore)

    /**
     * Copies an existing file-backed history into an empty database, once.
     *
     * This is what makes turning the database on a non-event: a deployment that has been recording runs
     * as files since 006 keeps its history instead of appearing to have lost it. It runs only when the
     * database holds no runs at all, so it cannot duplicate or overwrite anything on a later start, and
     * it never deletes a file — setting the backend back to `file` returns to exactly the old state.
     *
     * Records are imported oldest-first so the database's newest-write-first ordering comes out matching
     * the order the file store served them in.
     */
    private fun importFromFiles(dir: Path, into: Stores) {
        if (into.runs.recent(1).isNotEmpty()) return
        if (!dir.exists()) return
        val existing = FileRunStore(dir).recent(Int.MAX_VALUE)
        if (existing.isEmpty()) return

        val logs = dir.resolve(LOG_DIR)
        val fileLogs = if (logs.exists()) FileRunLogStore(logs) else null
        for (record in existing.asReversed()) {
            into.runs.record(record)
            fileLogs?.read(record.id)?.forEach { into.logs.append(record.id, it) }
        }
    }
}
