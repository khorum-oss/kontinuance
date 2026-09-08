package org.khorum.oss.kontinuance.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.DriverManager
import java.sql.Types

/**
 * The embedded SQLite database behind [SqliteRunStore] and [SqliteRunLogStore] (041) — one file, no
 * server process, inspectable with the ordinary `sqlite3` CLI.
 *
 * A connection is opened per operation rather than pooled: the workload is a handful of writes per run
 * and a query per API call, so the open cost is irrelevant beside the clarity of never sharing mutable
 * JDBC state across the coroutines that call in. Two settings make that safe:
 *
 * - **WAL journaling**, set once at migration and persisted in the file, so a reader (the API listing
 *   runs) never blocks the writer (a run recording its result), which the default rollback journal
 *   would do.
 * - **`busy_timeout`**, set on every connection, so a writer that finds the single write lock taken
 *   waits for it instead of failing the call outright.
 *
 * The schema is versioned in `schema_version` and migrated forward on open, so a database written by an
 * older build keeps working without a manual step.
 */
internal class SqliteDatabase(private val file: Path) {

    init {
        file.parent?.let { Files.createDirectories(it) }
        migrate()
    }

    /** Runs [block] against a fresh connection, closing it afterwards whether or not [block] threw. */
    fun <T> use(block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA busy_timeout = $BUSY_TIMEOUT_MS") }
            block(connection)
        }

    /** Runs [block] in a transaction, committing on success and rolling back on any failure. */
    fun <T> transaction(block: (Connection) -> T): T = use { connection ->
        connection.autoCommit = false
        connection.commitOrRollback { block(connection) }
    }

    /** Runs [statement], binding [bind], and reads the result set with [read]. */
    fun <T> query(statement: String, bind: (PreparedStatement) -> Unit, read: (ResultSet) -> T): T =
        use { connection ->
            connection.prepareStatement(statement).use {
                bind(it)
                it.executeQuery().use(read)
            }
        }

    private fun migrate() = use { connection ->
        connection.createStatement().use { statement ->
            // Durability over raw speed: `synchronous = FULL` is the setting that makes a recorded run
            // survive a host that loses power, which is the whole point of moving off "it was in a file".
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = FULL")
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)")
        }
        val current = connection.schemaVersion()
        MIGRATIONS.drop(current).forEachIndexed { i, statements ->
            connection.autoCommit = false
            connection.commitOrRollback { connection.apply(statements, version = current + i + 1) }
        }
    }

    private fun Connection.schemaVersion(): Int =
        prepareStatement("SELECT MAX(version) FROM schema_version").use { statement ->
            statement.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }

    /** Applies one migration's [statements] and stamps [version], within the caller's transaction. */
    private fun Connection.apply(statements: List<String>, version: Int) {
        createStatement().use { s -> statements.forEach(s::execute) }
        prepareStatement("INSERT INTO schema_version(version) VALUES (?)").use { s ->
            s.setInt(1, version)
            s.executeUpdate()
        }
    }

    /**
     * Commits [block]'s work, or rolls it back and rethrows. Catching [Throwable] is deliberate: an
     * uncommitted transaction left open by *any* failure would hold the write lock until the connection
     * closed, so every exit path has to roll back. The failure itself is always rethrown.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> Connection.commitOrRollback(block: () -> T): T =
        try {
            val result = block()
            commit()
            result
        } catch (e: Throwable) {
            rollback()
            throw e
        }

    private companion object {
        /** How long a write waits for the database's single write lock before giving up. */
        const val BUSY_TIMEOUT_MS = 5_000

        /**
         * Schema migrations in order; the applied count is the version. Append a new list to evolve the
         * schema — never edit one that has shipped, or a database in the field skips the change.
         *
         * `trigger` is spelled `trigger_kind` because `TRIGGER` is reserved in SQL. The nested per-step
         * breakdown lives in `stages_json`: it is only ever read back whole with its run, so a second
         * table would buy nothing and cost a join on every listing.
         */
        val MIGRATIONS: List<List<String>> = listOf(
            listOf(
                """
                CREATE TABLE runs (
                    id           TEXT PRIMARY KEY,
                    pipeline     TEXT NOT NULL,
                    status       TEXT NOT NULL,
                    failing_step TEXT,
                    reason       TEXT,
                    started_at   TEXT,
                    ended_at     TEXT,
                    repo         TEXT,
                    sha          TEXT,
                    trigger_kind TEXT,
                    project      TEXT,
                    stages_json  TEXT,
                    recorded_at  TEXT NOT NULL,
                    write_seq    INTEGER NOT NULL
                )
                """.trimIndent(),
                // The listing's own ordering, so `recent(limit)` is an index scan rather than a sort of
                // the whole history. Ordering keys off `write_seq`, a counter the insert advances, not off
                // `recorded_at`: two runs recorded in the same clock tick would otherwise order
                // arbitrarily, and "newest first" has to mean the same thing on every machine.
                // `recorded_at` stays for the human reading the file with the sqlite3 CLI.
                "CREATE UNIQUE INDEX runs_recency ON runs (write_seq DESC)",
                // Scoping runs to a project is the dashboard's most common question (039).
                "CREATE INDEX runs_project ON runs (project)",
                """
                CREATE TABLE run_logs (
                    run_id TEXT NOT NULL,
                    seq    INTEGER NOT NULL,
                    line   TEXT NOT NULL,
                    PRIMARY KEY (run_id, seq)
                )
                """.trimIndent(),
            ),
        )
    }
}

/**
 * Binds [values] to the statement's parameters in order, as strings or SQL NULL.
 *
 * Positional binding is what JDBC offers, and hand-numbering a dozen parameters is how a column ends up
 * silently holding its neighbour's value. Passing the values as one list in the order the statement
 * names its columns keeps the two readable side by side.
 */
internal fun PreparedStatement.bindStrings(values: List<String?>) {
    values.forEachIndexed { i, value ->
        if (value == null) setNull(i + 1, Types.VARCHAR) else setString(i + 1, value)
    }
}

/** Collects every row, mapping each with [row]. */
internal fun <T> ResultSet.mapRows(row: (ResultSet) -> T): List<T> = buildList {
    while (next()) add(row(this@mapRows))
}
