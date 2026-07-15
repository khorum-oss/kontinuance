package org.khorum.oss.kontinuance.persistence

/**
 * Records and reads back pipeline run history. Small on purpose so the file default can be replaced by
 * a database backend (in the Server/API feature) without changing callers (FR-004).
 */
interface RunStore {
    /** Records (or replaces, by id) a run. */
    fun record(record: RunRecord)

    /** The most recent runs, newest first, at most [limit]. */
    fun recent(limit: Int): List<RunRecord>

    /** The run with [id], or `null` if none. */
    fun get(id: String): RunRecord?
}

/** A store that discards everything — the "no store configured" sink so callers need no null checks. */
object NoOpRunStore : RunStore {
    override fun record(record: RunRecord) = Unit
    override fun recent(limit: Int): List<RunRecord> = emptyList()
    override fun get(id: String): RunRecord? = null
}

/** In-memory [RunStore] for tests and non-durable use; newest record wins per id. */
class InMemoryRunStore : RunStore {
    private val records = LinkedHashMap<String, RunRecord>()

    override fun record(record: RunRecord) {
        records.remove(record.id)
        records[record.id] = record
    }

    override fun recent(limit: Int): List<RunRecord> =
        records.values.toList().asReversed().take(limit)

    override fun get(id: String): RunRecord? = records[id]
}
