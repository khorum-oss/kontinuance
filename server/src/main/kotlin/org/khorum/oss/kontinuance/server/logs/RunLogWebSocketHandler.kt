package org.khorum.oss.kontinuance.server.logs

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import org.springframework.http.server.PathContainer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.util.pattern.PathPatternParser
import reactor.core.publisher.Mono

/**
 * WebFlux WebSocket endpoint (`/ws/runs/{id}/logs`, wired in
 * [org.khorum.oss.kontinuance.server.WebSocketConfig]) tailing one run's recorded output as text frames —
 * one frame per line, the same lines as the SSE tail (`/api/runs/{id}/logs/stream`, 023), for clients that
 * prefer a socket. It bridges the coroutine [RunLogStream] Flow to the reactive `send` pipeline with
 * `asFlux()` (kotlinx-coroutines-reactor); when the run reaches a terminal state the Flow completes, the
 * send pipeline completes, and the server closes the socket (the socket's own "end"). When the client
 * closes the socket the collector is cancelled, so [RunLogStream]'s polling stops. The run id is taken from
 * the socket path; a path without one is rejected with a close frame.
 */
@Component
class RunLogWebSocketHandler(private val stream: RunLogStream) : WebSocketHandler {

    private val pattern = PathPatternParser.defaultInstance.parse(PATH)

    override fun handle(session: WebSocketSession): Mono<Void> {
        val runId = runId(session) ?: return session.close(CloseStatus.BAD_DATA)
        val frames = stream.updates(runId)
            .map { line -> session.textMessage(line) }
            .asFlux()
        return session.send(frames)
    }

    /** Extracts the `{id}` path variable from the handshake URI (decoded), or null if the path is malformed. */
    private fun runId(session: WebSocketSession): String? {
        val path = PathContainer.parsePath(session.handshakeInfo.uri.rawPath)
        return pattern.matchAndExtract(path)?.uriVariables?.get("id")
    }

    companion object {
        /** The socket path pattern; also used by the handler mapping. */
        const val PATH = "/ws/runs/{id}/logs"
    }
}
