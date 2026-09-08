package org.khorum.oss.kontinuance.persistence

import java.nio.file.Path

/**
 * Durable [RunLogStore] backed by the same embedded SQLite database as [SqliteRunStore] (042), so a
 * run's record and its output live in one file that can be copied or backed up as a unit.
 *
 * Each line is a row carrying its position, and the position is chosen by the insert itself
 * (`MAX(seq) + 1` inside the statement), so two steps of one run appending concurrently can never take
 * the same slot or interleave mid-line — the guarantee [FileRunLogStore] gets from a per-id lock, here
 * enforced by the database instead of by process-local state.
 */
class SqliteRunLogStore(file: Path) : RunLogStore {

    private val db = SqliteDatabase(file)

    override fun append(runId: String, line: String) {
        db.transaction { connection ->
            connection.prepareStatement(APPEND).use { statement ->
                statement.bindStrings(listOf(runId, line, runId))
                statement.executeUpdate()
            }
        }
    }

    override fun read(runId: String): List<String> = db.query(
        "SELECT line FROM run_logs WHERE run_id = ? ORDER BY seq",
        bind = { it.setString(1, runId) },
        read = { rows -> rows.mapRows { it.getString("line") } },
    )

    private companion object {
        const val APPEND = """
            INSERT INTO run_logs (run_id, seq, line)
            SELECT ?, COALESCE(MAX(seq), 0) + 1, ? FROM run_logs WHERE run_id = ?
        """
    }
}
