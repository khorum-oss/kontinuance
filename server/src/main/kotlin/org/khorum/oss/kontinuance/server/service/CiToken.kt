package org.khorum.oss.kontinuance.server.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * The CI dispatch bearer token — the machine credential an external CI caller presents — bound from
 * `kontinuance.auth.ciToken` (env `KONTINUANCE_CI_TOKEN`).
 *
 * A separate principal from [AuthCredentials] on purpose. A robot that signs in as the operator inherits
 * configuration-write access and has no revocation path of its own: revoking it means changing the
 * password a human uses. A distinct token rotates without locking anyone out, and
 * [org.khorum.oss.kontinuance.server.config.CiScope] bounds what it can do.
 *
 * Compared in constant time and never logged, matching [AuthCredentials.matches].
 */
@Component
class CiToken(@param:Value("\${kontinuance.auth.ciToken:}") private val token: String) {

    /** True only when a token is configured — like [AuthCredentials], enforcement is opt-in. */
    val enabled: Boolean = token.isNotBlank()

    /** Constant-time comparison; always false when no token is configured. */
    fun matches(presented: String): Boolean {
        if (!enabled) return false
        return MessageDigest.isEqual(
            presented.toByteArray(Charsets.UTF_8),
            token.toByteArray(Charsets.UTF_8),
        )
    }
}
