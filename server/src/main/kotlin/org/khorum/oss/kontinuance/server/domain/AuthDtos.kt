package org.khorum.oss.kontinuance.server.domain

/**
 * Auth API DTOs (016), serialized by Jackson. Every response is a [SessionResponse] — the same shape so a
 * client reads its session state uniformly; `username`/`error` are null (omitted) when not applicable.
 */
data class SessionResponse(
    val authenticated: Boolean,
    val authRequired: Boolean,
    val username: String? = null,
    val error: String? = null,
)

/** `POST /api/auth/login` body. */
data class LoginRequest(val username: String? = null, val password: String? = null)
