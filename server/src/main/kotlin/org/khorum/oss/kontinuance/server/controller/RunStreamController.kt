package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.server.service.RunStream
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Server-Sent Events endpoint streaming run records as they appear: `GET /api/runs/stream` returns a
 * `text/event-stream` of `run` events, each carrying a [RunRecord] the framework's Jackson codec
 * serializes into the event `data` (the same shape as `/api/runs`). The handler returns a coroutine
 * [kotlinx.coroutines.flow.Flow] — WebFlux consumes it natively (via the kotlinx-coroutines-reactor bridge) and streams each
 * element without blocking. A disconnecting client cancels the Flow, stopping [RunStream]'s polling.
 */
@RestController
class RunStreamController(private val stream: RunStream) {

    @GetMapping("/api/runs/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): Flow<ServerSentEvent<RunRecord>> =
        stream.updates().map { record ->
            ServerSentEvent.builder(record).event("run").id(record.id).build()
        }
}