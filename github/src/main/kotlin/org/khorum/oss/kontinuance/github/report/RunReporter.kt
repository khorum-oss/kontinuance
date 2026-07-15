package org.khorum.oss.kontinuance.github.report

import kotlinx.coroutines.delay
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.github.client.CommitStatus
import org.khorum.oss.kontinuance.github.client.CommitStatus.State
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.client.GitHubClient
import org.khorum.oss.kontinuance.github.client.RepoRef

/**
 * Posts a run's lifecycle to GitHub as commit statuses on the head SHA: a `pending` status when a run
 * starts, then a terminal `success`/`failure`/`error` when it finishes (FR-003). All statuses use the
 * stable [KONTINUANCE_CHECK_CONTEXT]. Reporting retries with exponential backoff so a transient GitHub
 * outage does not lose the outcome (US1 acceptance scenario 4).
 *
 * @param client the GitHub seam.
 * @param maxAttempts total attempts per status post (>= 1).
 * @param baseBackoffMillis the first backoff delay; doubles each retry, capped by GitHub's Retry-After.
 */
class RunReporter(
    private val client: GitHubClient,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val baseBackoffMillis: Long = DEFAULT_BASE_BACKOFF_MILLIS,
) {

    /** Posts the `pending` status for a run that is about to start on [repo]@[sha]. */
    suspend fun reportPending(repo: RepoRef, sha: String, targetUrl: String? = null) {
        post(repo, sha, CommitStatus(State.PENDING, KONTINUANCE_CHECK_CONTEXT, "Run in progress", targetUrl))
    }

    /** Maps [run]'s terminal status to a commit status and posts it on [repo]@[sha]. */
    suspend fun reportOutcome(repo: RepoRef, sha: String, run: Run, targetUrl: String? = null) {
        val (state, description) = describe(run.status)
        post(repo, sha, CommitStatus(state, KONTINUANCE_CHECK_CONTEXT, description, targetUrl))
    }

    private fun describe(status: PipelineStatus): Pair<State, String> = when (status) {
        PipelineStatus.Success -> State.SUCCESS to "All stages passed"
        is PipelineStatus.Failed -> State.FAILURE to failureDescription(status)
        PipelineStatus.TimedOut -> State.FAILURE to "A step timed out"
        PipelineStatus.Cancelled -> State.ERROR to "Run cancelled"
        else -> State.ERROR to "Run ended in an unexpected state: ${status::class.simpleName}"
    }

    private fun failureDescription(failed: PipelineStatus.Failed): String {
        val step = failed.step?.let { "Failed at step '$it'" } ?: "Run failed"
        return "$step: ${failed.reason}".take(MAX_DESCRIPTION)
    }

    private suspend fun post(repo: RepoRef, sha: String, status: CommitStatus) {
        var attempt = 1
        while (true) {
            try {
                client.createCommitStatus(repo, sha, status)
                return
            } catch (e: GitHubApiException) {
                if (attempt >= maxAttempts) throw e
                delay(backoffMillis(attempt, e.retryAfterSeconds))
                attempt++
            }
        }
    }

    private fun backoffMillis(attempt: Int, retryAfterSeconds: Long?): Long {
        val exponential = baseBackoffMillis shl (attempt - 1)
        val retryAfter = retryAfterSeconds?.let { it * MILLIS_PER_SECOND } ?: 0L
        return maxOf(exponential, retryAfter)
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 4
        const val DEFAULT_BASE_BACKOFF_MILLIS = 500L
        const val MILLIS_PER_SECOND = 1000L
        const val MAX_DESCRIPTION = 140
    }
}
