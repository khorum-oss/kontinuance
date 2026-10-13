package org.khorum.oss.kontinuance.server.service

import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GiteaWebhookSecretTest {

    private val body = """{"ref":"refs/heads/main"}""".toByteArray()

    private fun sign(secret: String, bytes: ByteArray): String =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
            .doFinal(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `the hex hmac of the raw body verifies, in either case`() {
        val secret = GiteaWebhookSecret("s3cret")
        assertTrue(secret.verifies(body, sign("s3cret", body)))
        assertTrue(secret.verifies(body, sign("s3cret", body).uppercase()))
    }

    @Test
    fun `a signature over different bytes or under another secret does not`() {
        val secret = GiteaWebhookSecret("s3cret")
        assertFalse(secret.verifies(body + ' '.code.toByte(), sign("s3cret", body)))
        assertFalse(secret.verifies(body, sign("other", body)))
    }

    @Test
    fun `a missing or malformed signature never verifies`() {
        val secret = GiteaWebhookSecret("s3cret")
        assertFalse(secret.verifies(body, null))
        assertFalse(secret.verifies(body, ""))
        assertFalse(secret.verifies(body, "sha256=" + sign("s3cret", body)))
    }

    @Test
    fun `with no secret configured nothing verifies - not even the hmac under the empty key`() {
        val secret = GiteaWebhookSecret("")
        assertFalse(secret.enabled)
        assertFalse(secret.verifies(body, null))
        assertFalse(secret.verifies(body, "0".repeat(64)))
    }
}
