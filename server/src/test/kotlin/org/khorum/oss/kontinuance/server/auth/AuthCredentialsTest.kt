package org.khorum.oss.kontinuance.server.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit-verifies the credential gate (016): the [AuthCredentials.enabled] toggle (both-set only) and the
 * constant-time [AuthCredentials.matches] check (exact pair accepted; wrong username, wrong password, and
 * any input while disabled rejected).
 */
class AuthCredentialsTest {

    @Test
    fun `enabled only when both username and password are configured`() {
        assertTrue(AuthCredentials("operator", "s3cret").enabled)
        assertFalse(AuthCredentials("operator", "").enabled, "password missing")
        assertFalse(AuthCredentials("", "s3cret").enabled, "username missing")
        assertFalse(AuthCredentials("", "").enabled, "neither configured")
    }

    @Test
    fun `matches accepts the exact pair and rejects anything else`() {
        val creds = AuthCredentials("operator", "s3cret")
        assertTrue(creds.matches("operator", "s3cret"))
        assertFalse(creds.matches("operator", "wrong"), "wrong password")
        assertFalse(creds.matches("wrong", "s3cret"), "wrong username")
        assertFalse(creds.matches("wrong", "wrong"), "both wrong")
    }

    @Test
    fun `matches is always false when auth is disabled`() {
        assertFalse(AuthCredentials("", "").matches("", ""))
        assertFalse(AuthCredentials("operator", "").matches("operator", ""))
    }
}
