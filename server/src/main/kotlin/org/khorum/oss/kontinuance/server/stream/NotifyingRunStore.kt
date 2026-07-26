package org.khorum.oss.kontinuance.server.stream

import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore

/**
 * A [RunStore] decorator that fires a [RunChangeNotifier] run-signal after every in-process write, so a
 * push-mode stream wakes the instant a run is recorded (025). Reads delegate untouched. Wrapping the store
 * (rather than editing each write site) means every current and future writer signals automatically.
 */
class NotifyingRunStore(
    private val delegate: RunStore,
    private val notifier: RunChangeNotifier,
) : RunStore {

    override fun record(record: RunRecord) {
        delegate.record(record)
        notifier.signalRuns()
    }

    override fun recent(limit: Int): List<RunRecord> = delegate.recent(limit)

    override fun get(id: String): RunRecord? = delegate.get(id)
}
