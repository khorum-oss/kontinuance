package org.khorum.oss.kontinuance.server.config

import org.khorum.oss.kontinuance.server.domain.SessionResponse
import org.khorum.oss.kontinuance.server.service.AuthCredentials
import org.khorum.oss.kontinuance.server.service.CiToken
import org.khorum.oss.kontinuance.server.store.SessionStore
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

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
    private val ciToken: CiToken,
    private val mapper: ObjectMapper,
) : WebFilter, Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!credentials.enabled) return chain.filter(exchange)
        val path = exchange.request.path.pathWithinApplication().value()
        return if (isPublic(path) || hasValidSession(exchange) || hasCiToken(exchange, path)) {
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

    /**
     * A valid CI bearer token, presented for a route [CiScope] allows.
     *
     * Scope is checked *before* the token so an out-of-scope route answers identically whether or not the
     * presented token is real — otherwise the filter would be an oracle for probing a stolen token's
     * validity against routes it cannot use.
     */
    private fun hasCiToken(exchange: ServerWebExchange, path: String): Boolean {
        if (!CiScope.allows(path, exchange.request.method)) return false
        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return false
        if (!header.startsWith(BEARER)) return false
        return ciToken.matches(header.removePrefix(BEARER).trim())
    }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON
        val body = SessionResponse(authenticated = false, authRequired = true, error = "authentication required")
        val bytes = mapper.writeValueAsBytes(body)
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)))
    }

    private companion object {
        // Prefixes reachable without a session in every mode: the auth endpoints (else no one could sign
        // in), the API health check, and actuator (health only is exposed). Matched as an exact path or a
        // `<prefix>/…` sub-path so `/api/authx` does NOT match `/api/auth`.
        val publicPaths = listOf("/api/auth", "/api/health", "/actuator")

        const val BEARER = "Bearer "
    }
}
