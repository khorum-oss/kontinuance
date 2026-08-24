package org.khorum.oss.kontinuance.server.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The startup contract for the operator credential pair. A half-configured deployment is the failure this
 * guards: it used to run **open** with only a WARN, so a partially-applied secret looked protected while
 * serving every request unauthenticated. Now it refuses to start.
 */
class AuthCredentialsTest {

    private fun credentials(username: String = "", password: String = "", required: Boolean = false) =
        AuthCredentials(username, password, required)

    @Test
    fun `both credentials set enables enforcement`() {
        val subject = credentials(username = "operator", password = "s3cret")
        subject.afterPropertiesSet()

        assertTrue(subject.enabled)
    }

    @Test
    fun `neither credential set stays open when auth is not required`() {
        val subject = credentials()
        subject.afterPropertiesSet()

        assertTrue(!subject.enabled)
    }

    @Test
    fun `a username without a password refuses to start`() {
        val error = assertFailsWith<IllegalStateException> { credentials(username = "operator").afterPropertiesSet() }

        assertTrue(error.message!!.contains("kontinuance.auth.password"))
    }

    @Test
    fun `a password without a username refuses to start`() {
        val error = assertFailsWith<IllegalStateException> { credentials(password = "s3cret").afterPropertiesSet() }

        assertTrue(error.message!!.contains("kontinuance.auth.username"))
    }

    @Test
    fun `auth required with no credentials refuses to start`() {
        val error = assertFailsWith<IllegalStateException> { credentials(required = true).afterPropertiesSet() }

        assertTrue(error.message!!.contains("kontinuance.auth.required"))
    }

    @Test
    fun `auth required with both credentials starts and enforces`() {
        val subject = credentials(username = "operator", password = "s3cret", required = true)
        subject.afterPropertiesSet()

        assertTrue(subject.enabled)
    }

    @Test
    fun `the failure never echoes a configured value`() {
        val error = assertFailsWith<IllegalStateException> {
            credentials(username = "operator", password = "  ").afterPropertiesSet()
        }

        assertTrue(!error.message!!.contains("operator"))
    }

    @Test
    fun `blank-only values count as unset`() {
        val subject = credentials(username = "   ", password = "   ")
        subject.afterPropertiesSet()

        assertEquals(false, subject.enabled)
    }
}
