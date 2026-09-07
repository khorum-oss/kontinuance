package org.khorum.oss.kontinuance.server.domain

/**
 * `/api/config` DTOs (027), serialized by Jackson. [ConfigUpdateRequest] is the `PUT` body.
 *
 * [ConfigResponse] is the descriptor source text plus a resolved [PlanSummary], and — since 041 — where
 * the descriptor that would actually run comes from: `stored` (a descriptor saved on this server),
 * `repo` (read from the project's repository), or `live` (the server's descriptor file, used when no
 * project is active). [overridden] is true exactly when a stored descriptor is shadowing a repository
 * that also has one — a stored descriptor with no repository behind it is not "overriding" anything, so
 * showing a banner for it would be a lie. Both fields default so existing callers that only know the
 * pre-041 shape still compile.
 */
data class ConfigResponse(
    val source: String,
    val text: String,
    val plan: PlanSummary,
    val origin: String = "live",
    val overridden: Boolean = false,
)

data class PlanSummary(
    val stages: Int,
    val tasks: Int,
    val maxParallel: Int,
    val toolchain: String,
    val publish: String,
    val deploy: String,
)

/** `PUT /api/config` body: the edited descriptor `{ "text": … }`. */
data class ConfigUpdateRequest(val text: String? = null)
