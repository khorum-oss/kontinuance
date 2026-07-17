package org.khorum.oss.kontinuance.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

/**
 * Enables server-side WebFlux WebSockets: maps `/ws/runs` to [RunWebSocketHandler] and registers the
 * [WebSocketHandlerAdapter] that invokes it. A plain WebFlux app does not auto-configure these, so they
 * are declared explicitly. The mapping only claims `/ws/runs`; all other paths fall through to the
 * annotated `@RestController` routes.
 */
@Configuration
class WebSocketConfig {

    @Bean
    fun runWebSocketMapping(handler: RunWebSocketHandler): HandlerMapping =
        SimpleUrlHandlerMapping(mapOf("/ws/runs" to handler), Ordered.HIGHEST_PRECEDENCE)

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter = WebSocketHandlerAdapter()
}
