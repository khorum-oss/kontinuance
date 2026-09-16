package org.khorum.oss.kontinuance.github.client

/** A GitHub repository coordinate, e.g. `khorum-oss/kontinuance`. */
data class RepoRef(val owner: String, val name: String) {
    /** The `owner/name` slug used in API paths and logs. */
    val slug: String get() = "$owner/$name"

    init {
        require(owner.isNotBlank()) { "repo owner must be non-empty" }
        require(name.isNotBlank()) { "repo name must be non-empty" }
    }

    companion object {
        private val HTTPS = Regex("^https?://github\\.com/([A-Za-z0-9._-]+?)/([A-Za-z0-9._-]+?)(?:\\.git)?/?$")
        private val SSH = Regex("^git@github\\.com:([A-Za-z0-9._-]+?)/([A-Za-z0-9._-]+?)(?:\\.git)?/?$")

        /**
         * The [RepoRef] a repository URL names, or `null` when the URL is not a GitHub repository.
         *
         * `null` is a routine answer, not an error: a project may legitimately point at another host,
         * in which case it simply cannot use a repo-hosted descriptor (041, FR-002).
         */
        fun parse(url: String): RepoRef? {
            val match = HTTPS.find(url.trim()) ?: SSH.find(url.trim()) ?: return null
            val (owner, name) = match.destructured
            return RepoRef(owner, name)
        }
    }
}

/**
 * The minimal view of an open pull request the event source needs.
 *
 * [headRepo] is the `owner/name` of the repository the head branch lives in — which is **not** the watched
 * repository when the PR comes from a fork. Required rather than defaulted on purpose: the poller checks
 * this commit out and builds it on the runner host, so a producer that silently omitted the field would
 * hand arbitrary code from a stranger's fork to a machine holding the operator's toolchain, registry
 * credentials, and LAN access.
 */
data class PullRequest(
    val number: Int,
    val headSha: String,
    val headRef: String,
    val baseRef: String,
    val headRepo: String,
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
