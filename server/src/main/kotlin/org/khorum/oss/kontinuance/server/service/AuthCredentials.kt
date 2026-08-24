package org.khorum.oss.kontinuance.server.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * The single operator credential pair that gates the API (016). Bound from `kontinuance.auth.username` /
 * `kontinuance.auth.password` (env `KONTINUANCE_AUTH_USERNAME` / `KONTINUANCE_AUTH_PASSWORD`) via Spring's
 * relaxed binding, defaulting to empty. Authentication is [enabled] only when **both** are non-blank.
 *
 * A **half-configured** deployment refuses to start. 016 originally treated one-of-two as open; that made a
 * partially-applied secret indistinguishable from a deliberate open deployment — the server looked protected
 * and served every request unauthenticated behind a WARN nobody reads. Failing at bean initialization means
 * the process exits before the web server binds a port, so it can never serve openly by accident.
 *
 * `kontinuance.auth.required` (env `KONTINUANCE_AUTH_REQUIRED`, default `false`) lets a deployment assert
 * that authentication is mandatory: with it set, missing credentials are a startup failure rather than open
 * mode. That is the guard for a secret that fails to populate *either* variable. Leaving it unset preserves
 * the open-on-loopback default that local development and the test suite rely on.
 *
 * [matches] compares username and password in **constant time** (`MessageDigest.isEqual`) and combines the
 * two with a non-short-circuiting `and`, so a wrong username and a wrong password are indistinguishable and
 * timing does not reveal how many characters matched (FR-008). The configured password is never logged.
 */
@Component
class AuthCredentials(
    @param:Value("\${kontinuance.auth.username:}") private val username: String,
    @param:Value("\${kontinuance.auth.password:}") private val password: String,
    @param:Value("\${kontinuance.auth.required:false}") private val required: Boolean,
) : InitializingBean {

    /** True only when both a username and a password are configured — enforcement is opt-in (FR-001). */
    val enabled: Boolean = username.isNotBlank() && password.isNotBlank()

    /** Constant-time credential check; always false when auth is not enabled. */
    fun matches(user: String, pass: String): Boolean {
        if (!enabled) return false
        val userOk = MessageDigest.isEqual(user.toByteArray(Charsets.UTF_8), username.toByteArray(Charsets.UTF_8))
        val passOk = MessageDigest.isEqual(pass.toByteArray(Charsets.UTF_8), password.toByteArray(Charsets.UTF_8))
        return userOk and passOk
    }

    /**
     * Validates the credential configuration before the application can serve anything. Messages name the
     * offending *property*, never a configured value, so a failure is diagnosable from logs without leaking
     * the credential that was set.
     */
    override fun afterPropertiesSet() {
        val hasUsername = username.isNotBlank()
        val hasPassword = password.isNotBlank()
        if (hasUsername != hasPassword) {
            val missing = if (hasUsername) "kontinuance.auth.password" else "kontinuance.auth.username"
            error(
                "Kontinuance authentication is half-configured: $missing is not set. Set both credentials to " +
                    "enforce authentication, or neither to run open — a partial configuration would serve the " +
                    "API unauthenticated while appearing protected.",
            )
        }
        if (required && !enabled) {
            error(
                "kontinuance.auth.required is set, but no operator credentials are configured. Set " +
                    "kontinuance.auth.username and kontinuance.auth.password, or clear the required flag.",
            )
        }
        if (!enabled) {
            LoggerFactory.getLogger(AuthCredentials::class.java).warn(
                "Kontinuance API authentication is DISABLED (kontinuance.auth.username/password not set). " +
                    "The API is unauthenticated — run it on loopback or behind an authenticating proxy.",
            )
        }
    }
}
