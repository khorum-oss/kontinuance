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
)

data class SourceRepo(
    val slug: String,
    val prPipeline: String,
    val pushPipeline: String? = null,
    val trackedBranch: String,
)

data class SourceCursor(val key: String, val sha: String)

data class SourceHeartbeat(val lastPolledMillis: Long, val ageSeconds: Long, val stale: Boolean, val cycles: Long)
