package org.khorum.oss.kontinuance.server.domain.ci

/**
 * The CI dispatch request: an external caller (a GitHub Actions job) asking Kontinuance to build one
 * commit.
 *
 * [event] selects *which* of the repository's configured descriptors runs — `prPipeline` for [CiEvent.PR]
 * (the default when absent or unrecognised) and `pushPipeline` for [CiEvent.PUSH]. [pipeline] is optional
 * and, when given, is *confirmed* against whichever that selected — a caller may assert which pipeline it
 * expects, never select an arbitrary descriptor. [ref] and [prNumber] are accepted for the contract's sake but are not persisted:
 * [org.khorum.oss.kontinuance.persistence.RunRecord] has no field for either, and adding one would reach
 * into the store's schema for a display nicety.
 */
data class CiDispatchRequest(
    val repo: String,
    val sha: String,
    val ref: String? = null,
    val prNumber: Int? = null,
    val pipeline: String? = null,
    val event: CiEvent? = null,
)

/** The dispatch response: `{ "runId": …, "status": … }` — `Running` or `AlreadyRunning`. */
data class CiDispatchResponse(val runId: String, val status: String)
