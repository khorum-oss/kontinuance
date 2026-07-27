package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.server.ErrorResponse
import org.khorum.oss.kontinuance.server.StatusMessage
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Cancels an in-flight run: `POST /api/runs/{id}/cancel` requests cancellation via [RunCanceller]. Returns
 * `200 {"status":"cancelling"}` when an actively-running run was asked to stop (it ends `Cancelled`
 * shortly after), `409 {"error":…}` when the run is not currently running (already terminal, or waiting at
 * a gate — reject it instead), or `404 {"error":…}` for an unknown id.
 */
@RestController
class CancelController(private val canceller: RunCanceller) {

    @PostMapping("/api/runs/{id}/cancel")
    suspend fun cancel(@PathVariable id: String): ResponseEntity<*> =
        when (canceller.cancel(id)) {
            RunCanceller.Outcome.CANCELLING -> ResponseEntity.ok(StatusMessage("cancelling"))
            RunCanceller.Outcome.NOT_ACTIVE ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("run is not running: $id"))
            RunCanceller.Outcome.NOT_FOUND ->
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("no such run: $id"))
        }
}
