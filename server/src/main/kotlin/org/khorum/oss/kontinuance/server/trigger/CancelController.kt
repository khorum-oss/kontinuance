package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.server.JsonView
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Cancels an in-flight run: `POST /api/runs/{id}/cancel` requests cancellation via [RunCanceller]. Returns
 * `200 {"status":"cancelling"}` when an actively-running run was asked to stop (it ends `Cancelled`
 * shortly after), `409 {"error":…}` when the run is not currently running (already terminal, or waiting at
 * a gate — reject it instead), or `404 {"error":…}` for an unknown id. Body is raw bytes, matching the
 * other controllers.
 */
@RestController
class CancelController(private val canceller: RunCanceller) {

    @PostMapping("/api/runs/{id}/cancel")
    suspend fun cancel(@PathVariable id: String): ResponseEntity<ByteArray> =
        when (canceller.cancel(id)) {
            RunCanceller.Outcome.CANCELLING -> body(HttpStatus.OK, JsonView.message("status", "cancelling"))
            RunCanceller.Outcome.NOT_ACTIVE ->
                body(HttpStatus.CONFLICT, JsonView.message("error", "run is not running: $id"))
            RunCanceller.Outcome.NOT_FOUND ->
                body(HttpStatus.NOT_FOUND, JsonView.message("error", "no such run: $id"))
        }

    private fun body(status: HttpStatus, json: String): ResponseEntity<ByteArray> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.toByteArray())
}
