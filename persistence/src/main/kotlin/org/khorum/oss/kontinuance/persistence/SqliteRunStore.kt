package org.khorum.oss.kontinuance.persistence

import java.nio.file.Path
import java.sql.ResultSet
import java.time.Instant

/**
 * Durable [RunStore] backed by an embedded SQLite database (041) — the queryable alternative to
 * [FileRunStore], reachable through the same interface so no caller changes.
 *
 * What the database buys over one JSON file per run: a write is atomic (a reader never sees a
 * half-written record, which a partial file write could produce), listing is an index scan instead of
 * stat-ing and parsing every file in the directory, and `project` is a real indexed column, so scoping
 * the runs list server-side becomes a query rather than a client-side filter over everything loaded.
 *
 * Ordering matches [FileRunStore] exactly — newest **write** first, which is what that store's
 * modification-time sort meant — so a run that starts and later finishes rises to the top on each
 * write, and callers that rely on "the first record for a project is its latest run" keep working.
 * Unlike a modification time the key is a counter, so the order never depends on the host clock's
 * resolution: two runs recorded in the same instant still come back in the order they were written.
 *
 * A row whose stored breakdown cannot be parsed still lists, with no stages, rather than taking the
 * whole history down with it (FR-007, as the file store does for a corrupt file).
 */
class SqliteRunStore(file: Path) : RunStore {

    private val db = SqliteDatabase(file)

    override fun record(record: RunRecord) {
        db.transaction { connection ->
            connection.prepareStatement(UPSERT).use { statement ->
                // In the order UPSERT names its columns.
                statement.bindStrings(
                    listOf(
                        record.id,
                        record.pipeline,
                        record.status,
                        record.failingStep,
                        record.reason,
                        record.startedAt?.toString(),
                        record.endedAt?.toString(),
                        record.repo,
                        record.sha,
                        record.trigger,
                        record.project,
                        stagesToJson(record.stages),
                        Instant.now().toString(),
                    ),
                )
                statement.executeUpdate()
            }
        }
    }

    override fun recent(limit: Int): List<RunRecord> = db.query(
        "$SELECT_ALL ORDER BY write_seq DESC LIMIT ?",
        bind = { it.setInt(1, limit) },
        read = { rows -> rows.mapRows { it.toRecord() } },
    )

    override fun get(id: String): RunRecord? = db.query(
        "$SELECT_ALL WHERE id = ?",
        bind = { it.setString(1, id) },
        read = { rows -> if (rows.next()) rows.toRecord() else null },
    )

    private fun ResultSet.toRecord() = RunRecord(
        id = getString("id"),
        pipeline = getString("pipeline"),
        status = getString("status"),
        failingStep = getString("failing_step"),
        reason = getString("reason"),
        startedAt = getString("started_at")?.toInstantOrNull(),
        endedAt = getString("ended_at")?.toInstantOrNull(),
        repo = getString("repo"),
        sha = getString("sha"),
        trigger = getString("trigger_kind"),
        project = getString("project"),
        stages = stagesFromJson(getString("stages_json")),
    )

    /** An unparseable timestamp reads as absent rather than failing the whole listing (FR-007). */
    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    private companion object {
        const val SELECT_ALL = """
            SELECT id, pipeline, status, failing_step, reason, started_at, ended_at,
                   repo, sha, trigger_kind, project, stages_json
            FROM runs
        """

        // Replaces by id, exactly as the file store's write-the-file-again does: a run is recorded
        // `Running` first and terminal later, under the one id.
        const val UPSERT = """
            INSERT INTO runs (id, pipeline, status, failing_step, reason, started_at, ended_at,
                              repo, sha, trigger_kind, project, stages_json, recorded_at, write_seq)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    (SELECT COALESCE(MAX(write_seq), 0) + 1 FROM runs))
            ON CONFLICT(id) DO UPDATE SET
                pipeline     = excluded.pipeline,
                status       = excluded.status,
                failing_step = excluded.failing_step,
                reason       = excluded.reason,
                started_at   = excluded.started_at,
                ended_at     = excluded.ended_at,
                repo         = excluded.repo,
                sha          = excluded.sha,
                trigger_kind = excluded.trigger_kind,
                project      = excluded.project,
                stages_json  = excluded.stages_json,
                recorded_at  = excluded.recorded_at,
                -- Re-recording a run moves it to the front of the listing, exactly as rewriting its file
                -- did: a run that started and then finished is the most recent thing that happened.
                write_seq    = excluded.write_seq
        """
    }
}
