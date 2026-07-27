package org.khorum.oss.kontinuance.server

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

/**
 * WebFlux WebSocket endpoint (`/ws/runs`, wired in [WebSocketConfig]) pushing run records as text frames
 * of JSON — the same records and shape as the SSE stream, for clients that prefer a socket. Each record
 * is serialized with the shared Jackson [ObjectMapper] (so the frame shape matches `/api/runs`). Bridges
 * the coroutine [RunStream] Flow to the reactive `send` pipeline with `asFlux()`; when the client closes
 * the socket the send pipeline completes and the Flow collector is cancelled, so [RunStream]'s polling
 * stops.
 */
@Component
class RunWebSocketHandler(
    private val stream: RunStream,
    private val mapper: ObjectMapper,
) : WebSocketHandler {

    override fun handle(session: WebSocketSession): Mono<Void> {
        val frames = stream.updates()
            .map { record -> session.textMessage(mapper.writeValueAsString(record)) }
            .asFlux()
        return session.send(frames)
    }
}
