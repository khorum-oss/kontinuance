package org.khorum.oss.kontinuance.github.support

import org.khorum.oss.kontinuance.github.client.CommitStatus
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.client.GitHubClient
import org.khorum.oss.kontinuance.github.client.PullRequest
import org.khorum.oss.kontinuance.github.client.RepoRef

/**
 * An in-memory [GitHubClient] for unit tests: returns a fixed set of open PRs and records every
 * commit status posted. Can be told to fail the first [failuresBeforeSuccess] status posts (to
 * exercise reporter retry/backoff).
 */
class RecordingGitHubClient(
    private val pulls: List<PullRequest> = emptyList(),
    private var failuresBeforeSuccess: Int = 0,
) : GitHubClient {

    /** Every (repo, sha, status) successfully posted, in order. */
    val posted: MutableList<Triple<RepoRef, String, CommitStatus>> = mutableListOf()

    override suspend fun listOpenPullRequests(repo: RepoRef): List<PullRequest> = pulls

    override suspend fun createCommitStatus(repo: RepoRef, sha: String, status: CommitStatus) {
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess--
            throw GitHubApiException(SERVICE_UNAVAILABLE, "simulated GitHub outage")
        }
        posted += Triple(repo, sha, status)
    }

    private companion object {
        const val SERVICE_UNAVAILABLE = 503
    }
}
