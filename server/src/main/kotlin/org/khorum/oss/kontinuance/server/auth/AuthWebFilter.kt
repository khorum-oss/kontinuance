package org.khorum.oss.kontinuance.server.auth

import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * The single authentication choke point (016). A [WebFilter] runs before handler mapping for **every**
 * exchange, so this one gate uniformly covers the annotated controllers, the SSE stream, and the WebSocket
 * upgrade — no per-controller wiring.
 *
 * When credentials are not configured ([AuthCredentials.enabled] false) the filter is a pass-through (open
 * mode). When enabled, [publicPaths] are always allowed (the auth endpoints, the API health check, and
 * actuator health); every other path — including `/api/runs/stream` and `/ws/runs` — requires a valid
 * [SessionStore.COOKIE] session, else the request is rejected with `401` and a JSON body without reaching
 * the handler (FR-002 / FR-007).
 */
@Component
class AuthWebFilter(
    private val credentials: AuthCredentials,
    private val sessions: SessionStore,
) : WebFilter, Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!credentials.enabled) return chain.filter(exchange)
        val path = exchange.request.path.pathWithinApplication().value()
        return if (isPublic(path) || hasValidSession(exchange)) {
            chain.filter(exchange)
        } else {
            unauthorized(exchange)
        }
    }

    private fun isPublic(path: String): Boolean =
        publicPaths.any { prefix -> path == prefix || path.startsWith("$prefix/") }

    private fun hasValidSession(exchange: ServerWebExchange): Boolean {
        val token = exchange.request.cookies.getFirst(SessionStore.COOKIE)?.value ?: return false
        return sessions.usernameFor(token) != null
    }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON
        val bytes = AuthJson.body(authenticated = false, authRequired = true, error = "authentication required")
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)))
    }

    private companion object {
        // Prefixes reachable without a session in every mode: the auth endpoints (else no one could sign
        // in), the API health check, and actuator (health only is exposed). Matched as an exact path or a
        // `<prefix>/…` sub-path so `/api/authx` does NOT match `/api/auth`.
        val publicPaths = listOf("/api/auth", "/api/health", "/actuator")
    }
}
