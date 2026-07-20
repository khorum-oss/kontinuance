package org.khorum.oss.kontinuance.server.logs

import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.khorum.oss.kontinuance.server.JsonView
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * `GET /api/runs/{id}/logs` — the run's recorded, already-masked, step-prefixed output as
 * `{"runId":…,"lines":[…]}` (018). A run with no recorded output — including an unknown id — returns `200`
 * with an empty `lines` array (the log is "empty", not missing; the run record itself still 404s via
 * `/api/runs/{id}`). Body is raw `ByteArray` JSON, matching the other controllers.
 */
@RestController
class RunLogController(private val store: RunLogStore) {

    @GetMapping("/api/runs/{id}/logs")
    fun logs(@PathVariable id: String): ResponseEntity<ByteArray> =
        ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .body(JsonView.runLogs(id, store.read(id)).toByteArray())
}
