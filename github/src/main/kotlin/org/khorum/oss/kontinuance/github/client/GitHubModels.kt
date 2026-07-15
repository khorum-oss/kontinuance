package org.khorum.oss.kontinuance.github.client

/** A GitHub repository coordinate, e.g. `khorum-oss/kontinuance`. */
data class RepoRef(val owner: String, val name: String) {
    /** The `owner/name` slug used in API paths and logs. */
    val slug: String get() = "$owner/$name"

    init {
        require(owner.isNotBlank()) { "repo owner must be non-empty" }
        require(name.isNotBlank()) { "repo name must be non-empty" }
    }
}

/** The minimal view of an open pull request the event source needs. */
data class PullRequest(
    val number: Int,
    val headSha: String,
    val headRef: String,
    val baseRef: String,
)

/**
 * A commit status to post on a head SHA. The [context] is the stable check name a branch-protection
 * rule matches against; it MUST NOT change silently between runs (FR-004).
 */
data class CommitStatus(
    val state: State,
    val context: String,
    val description: String,
    val targetUrl: String? = null,
) {
    /** GitHub commit-status states. */
    enum class State(val wire: String) {
        PENDING("pending"),
        SUCCESS("success"),
        FAILURE("failure"),
        ERROR("error"),
    }
}
