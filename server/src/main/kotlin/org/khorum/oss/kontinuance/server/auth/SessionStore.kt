package org.khorum.oss.kontinuance.server.auth

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory session registry (016): maps an opaque session token to the signed-in username. A token is a
 * 32-byte `SecureRandom` value, base64url-encoded (header-safe, no padding), issued at login and carried by
 * the client as the [COOKIE] cookie. Sessions are not persisted and not shared across instances — they are
 * cleared on restart, consistent with the single-instance durability model. Lookup returns the username for
 * a live token; [revoke] removes it (logout), after which the token no longer authenticates.
 */
@Component
class SessionStore {

    private val sessions = ConcurrentHashMap<String, String>()
    private val random = SecureRandom()

    /** Create a session for [username] and return its token (the cookie value). */
    fun issue(username: String): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        sessions[token] = username
        return token
    }

    /** The signed-in username for [token], or null if the token is unknown/revoked. */
    fun usernameFor(token: String): String? = sessions[token]

    /** Invalidate [token] so it no longer authenticates. */
    fun revoke(token: String) {
        sessions.remove(token)
    }

    companion object {
        /** The session cookie name. */
        const val COOKIE = "KSESSION"
        private const val TOKEN_BYTES = 32
    }
}
