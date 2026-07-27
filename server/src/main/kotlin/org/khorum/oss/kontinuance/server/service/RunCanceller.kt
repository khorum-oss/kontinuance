package org.khorum.oss.kontinuance.server.service

import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.stereotype.Component

/**
 * Cancels an in-flight run (028). The run was launched with the server's id as its engine run id (see
 * [RunLauncher]), so [PipelineEngine.cancel]
 * can address it: the engine terminates the in-flight step and
 * the run ends `Cancelled`, which [RunLauncher]
 * then records — so the UI sees the flip (instantly under the
 * 025 push stream). Only an actively-running run is cancellable here; a terminal run is already done, and a
 * run paused at an approval gate is ended via reject (it is no longer executing), so both are reported
 * "not active" rather than silently no-op'd.
 */
@Component
class RunCanceller(
    private val store: RunStore,
    private val engine: PipelineEngine,
) {

    /** Requests cancellation of [id]. */
    suspend fun cancel(id: String): Outcome {
        val record = store.get(id) ?: return Outcome.NOT_FOUND
        if (!isCancellable(record.status)) return Outcome.NOT_ACTIVE
        engine.cancel(RunId(id))
        return Outcome.CANCELLING
    }

    /** A run is cancellable only while it is actively executing (running / pending), not waiting or done. */
    private fun isCancellable(status: String): Boolean {
        val s = status.lowercase()
        return s.startsWith("run") || s.startsWith("pend") || s.startsWith("queue")
    }

    enum class Outcome { CANCELLING, NOT_ACTIVE, NOT_FOUND }
}
