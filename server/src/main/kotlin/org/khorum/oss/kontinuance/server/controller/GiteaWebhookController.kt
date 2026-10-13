package org.khorum.oss.kontinuance.server.controller

import org.khorum.oss.kontinuance.server.domain.ErrorResponse
import org.khorum.oss.kontinuance.server.domain.ci.CiDispatchResponse
import org.khorum.oss.kontinuance.server.service.GiteaWebhookReceiver
import org.khorum.oss.kontinuance.server.service.GiteaWebhookReceiver.Outcome
import org.khorum.oss.kontinuance.server.service.GiteaWebhookSecret
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * `POST /api/hooks/gitea` — Gitea tells Kontinuance that a pull request or a branch changed (043).
 *
 * The one route that carries neither a session nor the CI bearer token: its authentication is the
 * delivery's signature, checked here over the raw bytes before anything reads them as JSON. `401` for a
 * missing or wrong signature — and for every request while no secret is configured.
 *
 * Past that: `202` started a run, `200` means that commit is already building, `204` is a delivery that
 * is not a reason to build, and `400` is one that should have built and could not, with the reason in
 * the body. Gitea records the response next to each delivery, so the reason is readable where an
 * operator would look for it.
 */
@RestController
class GiteaWebhookController(
    private val secret: GiteaWebhookSecret,
    private val receiver: GiteaWebhookReceiver,
) {

    @PostMapping(PATH)
    suspend fun deliver(
        @RequestHeader(name = "X-Gitea-Signature", required = false) signature: String?,
        @RequestHeader(name = "X-Gitea-Event", required = false) event: String?,
        @RequestBody body: ByteArray,
    ): ResponseEntity<*> {
        if (!secret.verifies(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse("signature required"))
        }
        return when (val outcome = receiver.receive(event, body)) {
            is Outcome.Started -> ResponseEntity.status(HttpStatus.ACCEPTED).body(CiDispatchResponse(outcome.id, "Running"))
            is Outcome.AlreadyRunning -> ResponseEntity.ok(CiDispatchResponse(outcome.id, "AlreadyRunning"))
            is Outcome.Ignored -> ResponseEntity.noContent().header(REASON_HEADER, outcome.reason).build<Unit>()
            is Outcome.Refused -> ResponseEntity.badRequest().body(ErrorResponse(outcome.reason))
        }
    }

    companion object {
        const val PATH = "/api/hooks/gitea"

        /** A 204 has no body, so the reason a delivery was ignored rides in a header Gitea's log shows. */
        const val REASON_HEADER = "X-Kontinuance-Ignored"
    }
}
