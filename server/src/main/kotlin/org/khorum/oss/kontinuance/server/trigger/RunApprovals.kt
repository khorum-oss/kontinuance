package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.execution.ApprovalDecision
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Instant

/**
 * Resolves a run paused at a manual-approval gate. Durable by construction: the paused run and its
 * completed stages live in the [RunStore], so [resolve] works from that persisted state alone — an
 * operator can approve after a server restart just the same.
 *
 * - **Approve**: grant the decision to the gate and re-launch the pipeline from its completed stages
 *   (they are skipped and reused), so it continues at the gate and runs on to completion — or pauses
 *   again at a later gate.
 * - **Reject**: end the run `Cancelled` directly (a deliberate stop); no re-execution needed.
 *
 * The descriptor is reloaded from the configured path to rebuild the pipeline; it is expected to be the
 * same descriptor the run was started from.
 */
@Component
class RunApprovals(
    private val store: RunStore,
    private val gate: ServerApprovalGate,
    private val launcher: RunLauncher,
    @param:Value("\${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    /** Resolves the run waiting at [id]'s gate with [decision]; `false` if no such run is waiting. */
    fun resolve(id: String, decision: ApprovalDecision): Boolean {
        val record = store.get(id)?.takeIf { it.status == WAITING } ?: return false
        return when (decision) {
            ApprovalDecision.APPROVED -> resume(id, record)
            ApprovalDecision.REJECTED -> {
                store.record(record.copy(status = "Cancelled", endedAt = Instant.now(), reason = REJECTED_REASON))
                true
            }
        }
    }

    private fun resume(id: String, record: RunRecord): Boolean {
        val pipeline = runCatching { PipelineDescriptor.load(descriptor) }.getOrNull() ?: return false
        gate.grant(id, ApprovalDecision.APPROVED)
        store.record(record.copy(status = "Running"))
        val completed = record.stages
            .filter { it.status == "Success" || it.status == "Skipped" }
            .map { it.toStageRun() }
        launcher.launch(id, pipeline, record.startedAt ?: Instant.now(), completed)
        return true
    }

    private companion object {
        const val WAITING = "WaitingOnApproval"
        const val REJECTED_REASON = "rejected at approval gate"
    }
}
