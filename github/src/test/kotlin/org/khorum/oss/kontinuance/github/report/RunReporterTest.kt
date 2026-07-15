package org.khorum.oss.kontinuance.github.report

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.github.client.CommitStatus.State
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.support.RecordingGitHubClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunReporterTest {

    private val repo = RepoRef("khorum-oss", "kontinuance")
    private val sha = "abc123"

    private fun run(status: PipelineStatus) = Run(RunId("r1"), Pipeline("p", emptyList()), status, emptyList())

    @Test
    fun `pending uses the stable context`() = runTest {
        val client = RecordingGitHubClient()
        RunReporter(client).reportPending(repo, sha)

        val (_, postedSha, status) = client.posted.single()
        assertEquals(sha, postedSha)
        assertEquals(State.PENDING, status.state)
        assertEquals(KONTINUANCE_CHECK_CONTEXT, status.context)
    }

    @Test
    fun `success maps to a success status`() = runTest {
        val client = RecordingGitHubClient()
        RunReporter(client).reportOutcome(repo, sha, run(PipelineStatus.Success))

        assertEquals(State.SUCCESS, client.posted.single().third.state)
    }

    @Test
    fun `failure maps to a failure status naming the failing step`() = runTest {
        val client = RecordingGitHubClient()
        RunReporter(client).reportOutcome(repo, sha, run(PipelineStatus.Failed("compile", "exit 3")))

        val status = client.posted.single().third
        assertEquals(State.FAILURE, status.state)
        assertTrue(status.description.contains("compile"), "description should name the failing step")
    }

    @Test
    fun `a transient outage is retried until it succeeds`() = runTest {
        val client = RecordingGitHubClient(failuresBeforeSuccess = 2)
        RunReporter(client, maxAttempts = 4, baseBackoffMillis = 1).reportPending(repo, sha)

        assertEquals(1, client.posted.size, "the status is eventually posted after retries")
    }

    @Test
    fun `reporting gives up after exhausting attempts`() = runTest {
        val client = RecordingGitHubClient(failuresBeforeSuccess = 10)
        assertFailsWith<GitHubApiException> {
            RunReporter(client, maxAttempts = 3, baseBackoffMillis = 1).reportPending(repo, sha)
        }
    }
}
