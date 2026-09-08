package org.khorum.oss.kontinuance.github.client

/**
 * The single external seam to GitHub. Kept behind an interface so the event source is unit-testable
 * with a fake and integration-testable against a stand-in HTTP server, with zero real-network
 * dependency (Constitution II). All calls are outbound only.
 */
interface GitHubClient {

    /** Lists the open pull requests for [repo] (their head SHA/ref and base ref). */
    suspend fun listOpenPullRequests(repo: RepoRef): List<PullRequest>

    /** The head commit SHA of [branch] on [repo], or `null` if the branch does not exist. */
    suspend fun branchHead(repo: RepoRef, branch: String): String?

    /**
     * The contents of [path] in [repo] at [ref], or `null` when there is no such file at that ref.
     *
     * Used to read a project's descriptor out of its repository (041). [ref] is a commit SHA at every
     * call site, so the descriptor a run parses and the code it builds come from one commit.
     */
    suspend fun fileAt(repo: RepoRef, path: String, ref: String): String?

    /** Posts [status] on [repo]@[sha]. Throws [GitHubApiException] on a non-success response. */
    suspend fun createCommitStatus(repo: RepoRef, sha: String, status: CommitStatus)
}

/** Raised when a GitHub API call returns a non-success HTTP status. Carries the code for backoff logic. */
class GitHubApiException(
    val statusCode: Int,
    message: String,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)
