package org.khorum.oss.kontinuance.server.controller.log

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.khorum.oss.kontinuance.server.domain.stream.RunLogStream
import org.springframework.beans.factory.annotation.Value
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
class RunLogStreamController(
    private val stream: RunLogStream,
    /**
     * How long the stream may stay silent before a keep-alive comment goes out. Must sit comfortably
     * under any intermediary's idle timeout — Cloudflare reaps a streamed response that transmits
     * nothing for roughly 100 seconds.
     */
    @param:Value("\${kontinuance.stream.heartbeat-ms:20000}") private val heartbeatMs: Long,
) {

    @GetMapping("/api/runs/{id}/logs/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@PathVariable id: String): Flow<ServerSentEvent<String>> = channelFlow {
        // The heartbeat keeps the connection from going idle through a long silent task. `:backend:test`
        // runs for minutes with no output, and an idle stream is reset by the proxy in front of this
        // server — measured 2026-09-29: 143s of silence, then
        // `curl: (92) HTTP/2 stream 1 was not closed cleanly: INTERNAL_ERROR (err 2)`. The build was
        // healthy; the CI job merely lost its log tail and then looked hung for ten minutes.
        //
        // It is a COMMENT, not a data event: comments carry no `data:` field, so every client ignores
        // them — including the CI script's `sed -n 's/^data://p'` filter. The bytes keep the connection
        // alive without polluting anyone's output.
        //
        // Launched as a child of this channelFlow so it is cancelled when the block returns. A naive
        // `merge` with an endless ticker would never complete, so the terminal `end` event below would
        // never be reached and the client would wait for it forever.
        val heartbeat = launch {
            while (true) {
                delay(heartbeatMs)
                send(ServerSentEvent.builder<String>().comment("ping").build())
            }
        }
        try {
            stream.updates(id).collect { line ->
                send(ServerSentEvent.builder(line).event("log").build())
            }
            // Reaching here means the log flow completed normally: the run is terminal. Tell the client
            // to stop reconnecting. On cancellation or error the collect throws and nothing is emitted.
            send(ServerSentEvent.builder("").event("end").build())
        } finally {
            heartbeat.cancel()
        }
    }
}
