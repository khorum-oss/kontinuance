package org.khorum.oss.kontinuance.server.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

/**
 * The authentication endpoints (016), all under the always-public `/api/auth` prefix so they are reachable
 * whether or not auth is enforced (the filter lets them through; each handler decides its own result):
 *
 * - `POST /api/auth/login` — validate credentials in constant time; on success issue a session and set the
 *   HttpOnly `KSESSION` cookie.
 * - `GET /api/auth/me` — report `authRequired` and, when a valid session is present, the signed-in username.
 * - `POST /api/auth/logout` — revoke the session and expire the cookie.
 *
 * In open mode (no credentials configured) login/me report `authRequired:false` so a client knows not to
 * gate its UI. Handlers return a typed [SessionResponse] the Jackson codec serializes. See
 * contracts/auth-api.md.
 */
@RestController
class AuthController(
    private val credentials: AuthCredentials,
    private val sessions: SessionStore,
) {

    @PostMapping("/api/auth/login")
    fun login(
        @RequestBody(required = false) request: LoginRequest?,
        exchange: ServerWebExchange,
    ): ResponseEntity<SessionResponse> {
        if (!credentials.enabled) {
            return ResponseEntity.ok(SessionResponse(authenticated = false, authRequired = false))
        }
        val username = request?.username
        val password = request?.password
        if (username == null || password == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SessionResponse(authenticated = false, authRequired = true, error = "malformed request body"))
        }
        if (!credentials.matches(username, password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SessionResponse(authenticated = false, authRequired = true, error = "invalid credentials"))
        }
        exchange.response.addCookie(sessionCookie(sessions.issue(username)))
        return ResponseEntity.ok(SessionResponse(authenticated = true, authRequired = true, username = username))
    }

    @GetMapping("/api/auth/me")
    fun me(exchange: ServerWebExchange): ResponseEntity<SessionResponse> {
        if (!credentials.enabled) {
            return ResponseEntity.ok(SessionResponse(authenticated = false, authRequired = false))
        }
        val username = currentUser(exchange)
        return if (username != null) {
            ResponseEntity.ok(SessionResponse(authenticated = true, authRequired = true, username = username))
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SessionResponse(authenticated = false, authRequired = true))
        }
    }

    @PostMapping("/api/auth/logout")
    fun logout(exchange: ServerWebExchange): ResponseEntity<SessionResponse> {
        exchange.request.cookies.getFirst(SessionStore.COOKIE)?.value?.let(sessions::revoke)
        exchange.response.addCookie(expiredCookie())
        return ResponseEntity.ok(SessionResponse(authenticated = false, authRequired = credentials.enabled))
    }

    private fun currentUser(exchange: ServerWebExchange): String? =
        exchange.request.cookies.getFirst(SessionStore.COOKIE)?.value?.let(sessions::usernameFor)

    private fun sessionCookie(token: String): ResponseCookie =
        ResponseCookie.from(SessionStore.COOKIE, token).httpOnly(true).sameSite("Lax").path("/").build()

    private fun expiredCookie(): ResponseCookie =
        ResponseCookie.from(SessionStore.COOKIE, "").httpOnly(true).sameSite("Lax").path("/").maxAge(0).build()
}
