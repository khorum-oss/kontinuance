package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.server.JsonView
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `POST /api/runs/trigger` — starts a run of the configured pipeline descriptor. Delegates to
 * [RunTrigger], which records a `Running` run immediately (so the UI sees it live via the SSE stream)
 * and executes the pipeline in the background. Returns `202 Accepted` with `{"runId":…}` once the run
 * is registered, or `400 Bad Request` with `{"error":…}` when no valid descriptor is configured. The
 * body is written as raw bytes for the same reason as the read controllers: WebFlux has no
 * `String`->`application/json` writer.
 */
@RestController
class TriggerController(private val trigger: RunTrigger) {

    @PostMapping("/api/runs/trigger")
    fun trigger(): ResponseEntity<ByteArray> = when (val result = trigger.trigger()) {
        is RunTrigger.Result.Accepted ->
            body(HttpStatus.ACCEPTED, JsonView.message("runId", result.id))
        is RunTrigger.Result.Rejected ->
            body(HttpStatus.BAD_REQUEST, JsonView.message("error", result.reason))
    }

    private fun body(status: HttpStatus, json: String): ResponseEntity<ByteArray> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.toByteArray())
}
