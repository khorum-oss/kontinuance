package org.khorum.oss.kontinuance.server.domain

/**
 * `/api/config` DTOs (027), serialized by Jackson. [ConfigResponse] is the descriptor source text plus a
 * resolved [PlanSummary]; [ConfigUpdateRequest] is the `PUT` body.
 */
data class ConfigResponse(val source: String, val text: String, val plan: PlanSummary)

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
