package org.khorum.oss.kontinuance.server.domain.ci

/**
 * The CI dispatch request: an external caller (a GitHub Actions job) asking Kontinuance to build one
 * commit.
 *
 * [pipeline] is optional and, when given, is *confirmed* against the repository's configured `prPipeline`
 * rather than used to choose one — a caller may assert which pipeline it expects, never select an
 * arbitrary descriptor. [ref] and [prNumber] are accepted for the contract's sake but are not persisted:
 * [org.khorum.oss.kontinuance.persistence.RunRecord] has no field for either, and adding one would reach
 * into the store's schema for a display nicety.
 */
data class CiDispatchRequest(
    val repo: String,
    val sha: String,
    val ref: String? = null,
    val prNumber: Int? = null,
    val pipeline: String? = null,
)

/** The dispatch response: `{ "runId": …, "status": … }` — `Running` or `AlreadyRunning`. */
data class CiDispatchResponse(val runId: String, val status: String)
