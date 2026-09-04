package org.khorum.oss.kontinuance.server.domain

/**
 * `/api/source` DTOs (035/036), serialized by Jackson. When `configured` is false every other field is null
 * (omitted). The token env-var **name** is exposed, never a token value.
 */
data class SourceResponse(
    val configured: Boolean,
    val pollIntervalSeconds: Long? = null,
    val baseUrl: String? = null,
    val tokenEnv: String? = null,
    val repositories: List<SourceRepo>? = null,
    val cursors: List<SourceCursor>? = null,
    val heartbeat: SourceHeartbeat? = null,
    /** Whether the server is polling right now (040) — false for a config that exists but is stopped. */
    val running: Boolean = false,
    /** Whether a token is available, from the environment or stored. Never the token itself. */
    val hasToken: Boolean = false,
    /** Whether this server can connect a source itself, or only display one the CLI runs (040). */
    val manageable: Boolean = false,
)

/**
 * `POST /api/source` request (040): connect a repository from the dashboard. [token] is write-only — it is
 * stored for the poller and never returned by any read. Omit it to reuse an already-stored token or the
 * environment variable named by [tokenEnv].
 */
data class ConnectSourceRequest(
    val owner: String,
    val name: String,
    val prPipeline: String,
    val pushPipeline: String? = null,
    val trackedBranch: String? = null,
    val token: String? = null,
    val tokenEnv: String? = null,
    val baseUrl: String? = null,
    val pollIntervalSeconds: Long? = null,
)

data class SourceRepo(
    val slug: String,
    val prPipeline: String,
    val pushPipeline: String? = null,
    val trackedBranch: String,
)

data class SourceCursor(val key: String, val sha: String)

data class SourceHeartbeat(val lastPolledMillis: Long, val ageSeconds: Long, val stale: Boolean, val cycles: Long)
