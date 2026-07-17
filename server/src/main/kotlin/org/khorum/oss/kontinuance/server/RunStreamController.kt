package org.khorum.oss.kontinuance.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Server-Sent Events endpoint streaming run records as they appear: `GET /api/runs/stream` returns a
 * `text/event-stream` of `run` events, each carrying the record's JSON (the same shape as `/api/runs`).
 * The handler returns a coroutine [Flow] — WebFlux consumes it natively (via the kotlinx-coroutines-
 * reactor bridge) and streams each element without blocking. A disconnecting client cancels the Flow,
 * stopping [RunStream]'s polling (structured concurrency).
 */
@RestController
class RunStreamController(private val stream: RunStream) {

    @GetMapping("/api/runs/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): Flow<ServerSentEvent<String>> =
        stream.updates().map { record ->
            ServerSentEvent.builder(record.toJson()).event("run").id(record.id).build()
        }
}
