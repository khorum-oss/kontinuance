package org.khorum.oss.kontinuance.server.store

import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.khorum.oss.kontinuance.server.service.RunChangeNotifier

/**
 * A [RunLogStore] decorator that fires a [RunChangeNotifier] log-signal after every appended line, so a
 * push-mode log tail wakes the instant a line is recorded (025). Reads delegate untouched. Wrapping the
 * store means the engine's recording sink signals automatically, with no change to the sink.
 */
class NotifyingRunLogStore(
    private val delegate: RunLogStore,
    private val notifier: RunChangeNotifier,
) : RunLogStore {

    override fun append(runId: String, line: String) {
        delegate.append(runId, line)
        notifier.signalLog(runId)
    }

    override fun read(runId: String): List<String> = delegate.read(runId)
}
