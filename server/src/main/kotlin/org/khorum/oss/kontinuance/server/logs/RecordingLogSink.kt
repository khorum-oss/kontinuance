package org.khorum.oss.kontinuance.server.logs

import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.logging.StdoutLogSink
import org.khorum.oss.kontinuance.persistence.RunLogStore

/**
 * A per-run [LogSink] (018) the server binds to a run id and passes into `engine.run(logSink = …)`. Each
 * already-masked line the engine emits is appended to the [RunLogStore] under [runId] (so the UI can read
 * the run's output) and also teed to [tee] (stdout by default) so the container's own logs still show it.
 * Masking is inherited: the engine masks before this sink is called, so only redacted lines are stored.
 */
class RecordingLogSink(
    private val runId: String,
    private val store: RunLogStore,
    private val tee: LogSink = StdoutLogSink(),
) : LogSink {
    override fun emit(line: String) {
        store.append(runId, line)
        tee.emit(line)
    }
}
