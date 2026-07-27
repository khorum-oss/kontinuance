package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.server.ErrorResponse
import org.khorum.oss.kontinuance.server.RunIdResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `POST /api/runs/trigger` — starts a run of the configured pipeline descriptor. Delegates to
 * [RunTrigger], which records a `Running` run immediately (so the UI sees it live via the SSE stream)
 * and executes the pipeline in the background. Returns `202 Accepted` with `{"runId":…}` once the run
 * is registered, or `400 Bad Request` with `{"error":…}` when no valid descriptor is configured.
 */
@RestController
class TriggerController(private val trigger: RunTrigger) {

    @PostMapping("/api/runs/trigger")
    fun trigger(): ResponseEntity<*> = when (val result = trigger.trigger()) {
        is RunTrigger.Result.Accepted ->
            ResponseEntity.status(HttpStatus.ACCEPTED).body(RunIdResponse(result.id))
        is RunTrigger.Result.Rejected ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(result.reason))
    }
}
