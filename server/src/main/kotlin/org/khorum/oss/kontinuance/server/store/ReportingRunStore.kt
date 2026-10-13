package org.khorum.oss.kontinuance.server.store

import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.service.GiteaStatusReporter

/**
 * A [RunStore] decorator that shows every write to the [GiteaStatusReporter] (043), for the same reason
 * [NotifyingRunStore] exists: wrapping the store means every current and future writer is covered, where
 * editing each write site would miss the next one. The write happens first — a run is recorded whether or
 * not anything can be reported about it.
 */
class ReportingRunStore(
    private val delegate: RunStore,
    private val reporter: GiteaStatusReporter,
) : RunStore by delegate {

    override fun record(record: RunRecord) {
        delegate.record(record)
        reporter.observe(record)
    }
}
