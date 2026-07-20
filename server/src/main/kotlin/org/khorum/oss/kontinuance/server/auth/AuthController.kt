package org.khorum.oss.kontinuance.server.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
 * gate its UI. Responses are pre-serialized JSON written as `ResponseEntity<ByteArray>`, matching the other
 * controllers (WebFlux has no `String`->`application/json` writer). See contracts/auth-api.md.
 */
@RestController
class AuthController(
    private val credentials: AuthCredentials,
    private val sessions: SessionStore,
) {

    @PostMapping("/api/auth/login")
    suspend fun login(
        @RequestBody(required = false) requestBody: String?,
        exchange: ServerWebExchange,
    ): ResponseEntity<ByteArray> {
        if (!credentials.enabled) {
            return respond(HttpStatus.OK, AuthJson.body(authenticated = false, authRequired = false))
        }
        val creds = parseCredentials(requestBody)
            ?: return respond(
                HttpStatus.BAD_REQUEST,
                AuthJson.body(authenticated = false, authRequired = true, error = "malformed request body"),
            )
        if (!credentials.matches(creds.username, creds.password)) {
            return respond(
                HttpStatus.UNAUTHORIZED,
                AuthJson.body(authenticated = false, authRequired = true, error = "invalid credentials"),
            )
        }
        val token = sessions.issue(creds.username)
        exchange.response.addCookie(sessionCookie(token))
        return respond(HttpStatus.OK, AuthJson.body(authenticated = true, authRequired = true, username = creds.username))
    }

    @GetMapping("/api/auth/me")
    fun me(exchange: ServerWebExchange): ResponseEntity<ByteArray> {
        if (!credentials.enabled) {
            return respond(HttpStatus.OK, AuthJson.body(authenticated = false, authRequired = false))
        }
        val username = currentUser(exchange)
        return if (username != null) {
            respond(HttpStatus.OK, AuthJson.body(authenticated = true, authRequired = true, username = username))
        } else {
            respond(HttpStatus.UNAUTHORIZED, AuthJson.body(authenticated = false, authRequired = true))
        }
    }

    @PostMapping("/api/auth/logout")
    fun logout(exchange: ServerWebExchange): ResponseEntity<ByteArray> {
        exchange.request.cookies.getFirst(SessionStore.COOKIE)?.value?.let(sessions::revoke)
        exchange.response.addCookie(expiredCookie())
        return respond(HttpStatus.OK, AuthJson.body(authenticated = false, authRequired = credentials.enabled))
    }

    private fun currentUser(exchange: ServerWebExchange): String? =
        exchange.request.cookies.getFirst(SessionStore.COOKIE)?.value?.let(sessions::usernameFor)

    private fun sessionCookie(token: String): ResponseCookie =
        ResponseCookie.from(SessionStore.COOKIE, token).httpOnly(true).sameSite("Lax").path("/").build()

    private fun expiredCookie(): ResponseCookie =
        ResponseCookie.from(SessionStore.COOKIE, "").httpOnly(true).sameSite("Lax").path("/").maxAge(0).build()

    private fun respond(status: HttpStatus, body: ByteArray): ResponseEntity<ByteArray> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body)

    // Parse `{username, password}` with kotlinx-serialization (no jackson-module-kotlin dependency). Any
    // missing field or non-JSON body yields null → a 400 from the caller.
    private fun parseCredentials(requestBody: String?): Credentials? {
        if (requestBody.isNullOrBlank()) return null
        return runCatching {
            val obj = Json.parseToJsonElement(requestBody).jsonObject
            val user = obj["username"]?.jsonPrimitive?.content
            val pass = obj["password"]?.jsonPrimitive?.content
            if (user != null && pass != null) Credentials(user, pass) else null
        }.getOrNull()
    }

    private data class Credentials(val username: String, val password: String)
}
