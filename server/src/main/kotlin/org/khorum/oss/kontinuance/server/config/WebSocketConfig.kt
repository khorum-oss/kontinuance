package org.khorum.oss.kontinuance.server.config

import org.khorum.oss.kontinuance.server.socket.RunLogWebSocketHandler
import org.khorum.oss.kontinuance.server.socket.RunWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

/**
 * Enables server-side WebFlux WebSockets: maps `/ws/runs` to [RunWebSocketHandler] (the runs-list stream)
 * and `/ws/runs/{id}/logs` to [RunLogWebSocketHandler] (the per-run log tail, 029), and registers the
 * [WebSocketHandlerAdapter] that invokes them. A plain WebFlux app does not auto-configure these, so they
 * are declared explicitly. The mapping only claims the `/ws/` socket paths; all other paths fall through to
 * the annotated `@RestController` routes.
 */
@Configuration
class WebSocketConfig {

    @Bean
    fun runWebSocketMapping(
        runs: RunWebSocketHandler,
        logs: RunLogWebSocketHandler,
    ): HandlerMapping =
        SimpleUrlHandlerMapping(
            mapOf("/ws/runs" to runs, RunLogWebSocketHandler.PATH to logs),
            Ordered.HIGHEST_PRECEDENCE,
        )

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter = WebSocketHandlerAdapter()
}
