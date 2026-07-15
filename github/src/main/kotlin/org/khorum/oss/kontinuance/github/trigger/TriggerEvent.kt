package org.khorum.oss.kontinuance.github.trigger

import org.khorum.oss.kontinuance.github.client.RepoRef

/**
 * A repository event that should start a pipeline run: a PR head commit, a push to a tracked branch,
 * or an operator's manual request. [sha] is the immutable commit the run executes against and reports
 * on (the delivery `KONTINUANCE_SHA`).
 */
data class TriggerEvent(
    val repo: RepoRef,
    val sha: String,
    val kind: Kind,
    val ref: String,
    val pullNumber: Int? = null,
) {
    /** How the event was observed. */
    enum class Kind { PULL_REQUEST, PUSH, MANUAL }

    /**
     * The identity used to dedup triggers so the same commit/pipeline runs once even if the poller
     * re-lists it or a webhook is re-delivered (US3 acceptance scenario 3 / edge case "duplicate delivery").
     */
    val dedupKey: String get() = "${repo.slug}@$sha#${kind.name}"
}
