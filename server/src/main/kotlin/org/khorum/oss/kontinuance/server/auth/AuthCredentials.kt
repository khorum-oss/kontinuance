package org.khorum.oss.kontinuance.server.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * The single operator credential pair that gates the API (016). Bound from `kontinuance.auth.username` /
 * `kontinuance.auth.password` (env `KONTINUANCE_AUTH_USERNAME` / `KONTINUANCE_AUTH_PASSWORD`) via Spring's
 * relaxed binding, defaulting to empty. Authentication is [enabled] only when **both** are non-blank; a
 * half-set state is treated as open (spec Edge Cases). When open, a single WARN is logged at startup so the
 * operator knows the API is unauthenticated.
 *
 * [matches] compares username and password in **constant time** (`MessageDigest.isEqual`) and combines the
 * two with a non-short-circuiting `and`, so a wrong username and a wrong password are indistinguishable and
 * timing does not reveal how many characters matched (FR-008). The configured password is never logged.
 */
@Component
class AuthCredentials(
    @param:Value("\${kontinuance.auth.username:}") private val username: String,
    @param:Value("\${kontinuance.auth.password:}") private val password: String,
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

    override fun afterPropertiesSet() {
        if (!enabled) {
            LoggerFactory.getLogger(AuthCredentials::class.java).warn(
                "Kontinuance API authentication is DISABLED (kontinuance.auth.username/password not set). " +
                    "The API is unauthenticated — run it on loopback or behind an authenticating proxy.",
            )
        }
    }
}
