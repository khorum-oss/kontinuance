package org.khorum.oss.kontinuance.server.auth

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds the auth API's JSON responses (016) with the runtime `kotlinx-serialization-json` (no new
 * dependency). Every response has the same shape — `{authenticated, authRequired, username?, error?}` — so
 * a client can read its session state uniformly (see contracts/auth-api.md). Returns raw bytes because the
 * WebFlux controllers write pre-serialized JSON as `ResponseEntity<ByteArray>` (WebFlux has no
 * `String`->`application/json` writer), matching the other controllers' style.
 */
internal object AuthJson {

    fun body(
        authenticated: Boolean,
        authRequired: Boolean,
        username: String? = null,
        error: String? = null,
    ): ByteArray = buildJsonObject {
        put("authenticated", authenticated)
        put("authRequired", authRequired)
        username?.let { put("username", it) }
        error?.let { put("error", it) }
    }.toString().toByteArray()
}
