package org.khorum.oss.kontinuance.server.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The shared secret Gitea signs webhook deliveries with — bound from `kontinuance.hooks.gitea.secret`
 * (env `KONTINUANCE_GITEA_WEBHOOK_SECRET`).
 *
 * Gitea sends `X-Gitea-Signature`: the lowercase hex HMAC-SHA256 of the **raw request body**. The check
 * therefore runs over the bytes as received, before anything parses them, and compares in constant time
 * like [AuthCredentials.matches]. The secret is never logged.
 *
 * With no secret configured nothing verifies: the endpoint fails closed rather than treating "unsigned"
 * as acceptable. It does not refuse startup the way a half-configured operator credential does, because
 * the receiver ships to hosts before their secret exists (043).
 */
@Component
class GiteaWebhookSecret(@param:Value("\${kontinuance.hooks.gitea.secret:}") private val secret: String) {

    /** True only when a secret is configured. */
    val enabled: Boolean = secret.isNotBlank()

    /** True when [signature] is the hex HMAC-SHA256 of [body] under the configured secret. */
    fun verifies(body: ByteArray, signature: String?): Boolean {
        if (!enabled || signature == null) return false
        val presented = signature.trim().lowercase()
        if (!HEX_DIGEST.matches(presented)) return false
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM)) }
        val expected = mac.doFinal(body).joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), presented.toByteArray(Charsets.UTF_8))
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        val HEX_DIGEST = Regex("[0-9a-f]{64}")
    }
}
