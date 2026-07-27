package org.khorum.oss.kontinuance.server.controller.log

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import org.khorum.oss.kontinuance.server.domain.stream.RunLogStream
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Server-Sent Events endpoint tailing one run's output live (023): `GET /api/runs/{id}/logs/stream`
 * returns a `text/event-stream` of `log` events — one per recorded line, in order — followed by a
 * terminal `end` event once the run reaches a terminal state and the flow completes normally. The
 * handler returns a coroutine [Flow]; WebFlux consumes it natively (kotlinx-coroutines-reactor) and
 * streams each element without blocking. A disconnecting client cancels the Flow, stopping
 * [RunLogStream]'s polling (structured concurrency). The non-streaming `GET /api/runs/{id}/logs` (018)
 * remains for a one-shot fetch; this is the near-live tail the run-detail view watches.
 */
@RestController
class RunLogStreamController(private val stream: RunLogStream) {

    @GetMapping("/api/runs/{id}/logs/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@PathVariable id: String): Flow<ServerSentEvent<String>> =
        stream.updates(id)
            .map { line -> ServerSentEvent.builder(line).event("log").build() }
            .onCompletion { cause ->
                // Normal completion means the run reached a terminal state — signal the client to stop
                // reconnecting. On cancellation/error (cause != null) we emit nothing.
                if (cause == null) emit(ServerSentEvent.builder("").event("end").build())
            }
}
