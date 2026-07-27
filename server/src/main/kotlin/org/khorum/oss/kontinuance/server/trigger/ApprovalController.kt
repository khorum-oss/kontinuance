package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.engine.execution.ApprovalDecision
import org.khorum.oss.kontinuance.server.ErrorResponse
import org.khorum.oss.kontinuance.server.StatusMessage
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Resolves a run paused at a manual-approval gate: `POST /api/runs/{id}/approve` continues it,
 * `POST /api/runs/{id}/reject` ends it Cancelled. Returns `200 {"status":…}` when a run was waiting,
 * or `404 {"error":…}` when no run with that id is currently at a gate (already resolved or unknown id).
 * Resolution is durable — it works from the persisted run, so it survives a restart.
 */
@RestController
class ApprovalController(private val approvals: RunApprovals) {

    @PostMapping("/api/runs/{id}/approve")
    fun approve(@PathVariable id: String): ResponseEntity<*> =
        respond(id, approvals.resolve(id, ApprovalDecision.APPROVED), "approved")

    @PostMapping("/api/runs/{id}/reject")
    fun reject(@PathVariable id: String): ResponseEntity<*> =
        respond(id, approvals.resolve(id, ApprovalDecision.REJECTED), "rejected")

    private fun respond(id: String, handled: Boolean, action: String): ResponseEntity<*> =
        if (handled) {
            ResponseEntity.ok(StatusMessage(action))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("no run awaiting approval: $id"))
        }
}
