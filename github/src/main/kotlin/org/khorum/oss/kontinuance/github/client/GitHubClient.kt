package org.khorum.oss.kontinuance.github.client

/**
 * The single external seam to GitHub. Kept behind an interface so the event source is unit-testable
 * with a fake and integration-testable against a stand-in HTTP server, with zero real-network
 * dependency (Constitution II). All calls are outbound only.
 */
interface GitHubClient {

    /** Lists the open pull requests for [repo] (their head SHA/ref and base ref). */
    suspend fun listOpenPullRequests(repo: RepoRef): List<PullRequest>

    /** Posts [status] on [repo]@[sha]. Throws [GitHubApiException] on a non-success response. */
    suspend fun createCommitStatus(repo: RepoRef, sha: String, status: CommitStatus)
}

/** Raised when a GitHub API call returns a non-success HTTP status. Carries the code for backoff logic. */
class GitHubApiException(
    val statusCode: Int,
    message: String,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)
