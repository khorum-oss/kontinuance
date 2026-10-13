package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.RunRecord
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GiteaStatusReporterTest {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val posts = CopyOnWriteArrayList<Triple<String, String, CommitStatusPoster.Status>>()
    private var failuresLeft = 0

    private val reporter = GiteaStatusReporter(
        poster = { repo, sha, status ->
            if (failuresLeft > 0) {
                failuresLeft--
                Result.failure(IllegalStateException("gitea is down"))
            } else {
                posts += Triple(repo.slug, sha, status)
                Result.success(Unit)
            }
        },
        scope = scope,
        runUrlBase = "https://kontinuance.example/",
    )

    @AfterEach
    fun stop() = scope.cancel()

    private fun record(status: String, trigger: String? = "gitea-pr", id: String = "run-1", reason: String? = null) =
        RunRecord(
            id = id, pipeline = "p", status = status, reason = reason, startedAt = Instant.EPOCH,
            repo = "khorum-oss/relikquary", sha = "a".repeat(40), trigger = trigger,
        )

    private fun awaitPosts(count: Int) = runBlocking {
        withTimeout(10_000) { while (posts.size < count) kotlinx.coroutines.delay(10) }
    }

    @Test
    fun `a gate run is pending, then its verdict, under the stable check name with a link to the run`() {
        reporter.observe(record("Running"))
        reporter.observe(record("Success"))
        awaitPosts(2)

        assertEquals(listOf("pending", "success"), posts.map { it.third.state })
        assertEquals(setOf("kontinuance/ci"), posts.map { it.third.context }.toSet())
        assertEquals("khorum-oss/relikquary", posts[0].first)
        assertEquals("a".repeat(40), posts[0].second)
        assertEquals("https://kontinuance.example/runs/run-1", posts[0].third.targetUrl)
    }

    @Test
    fun `the engine rewriting a running record does not post pending again`() {
        repeat(5) { reporter.observe(record("Running")) }
        reporter.observe(record("Failed", reason = "step x exited 1"))
        awaitPosts(2)

        assertEquals(listOf("pending", "failure"), posts.map { it.third.state })
        assertEquals("Run failed: step x exited 1", posts[1].third.description)
    }

    @Test
    fun `a delivery run reports under its own check name`() {
        reporter.observe(record("Success", trigger = "gitea-push"))
        awaitPosts(1)

        assertEquals("kontinuance/deliver", posts.single().third.context)
    }

    @Test
    fun `a run paused at an approval gate stays pending until it is resumed and finishes`() {
        reporter.observe(record("Running"))
        reporter.observe(record("WaitingOnApproval"))
        reporter.observe(record("Running"))
        reporter.observe(record("Success"))
        awaitPosts(2)

        assertEquals(listOf("pending", "success"), posts.map { it.third.state })
    }

    @Test
    fun `a cancelled run is an error, not a failure of the code`() {
        reporter.observe(record("Cancelled"))
        awaitPosts(1)

        assertEquals("error", posts.single().third.state)
    }

    @Test
    fun `runs nobody asked gitea to expect are not reported`() {
        reporter.observe(record("Success", trigger = "github-actions", id = "a"))
        reporter.observe(record("Success", trigger = null, id = "b"))
        reporter.observe(record("Success", trigger = "gitea-pr", id = "c").copy(sha = null))
        reporter.observe(record("Success", id = "d"))
        awaitPosts(1)

        assertEquals(1, posts.size)
        assertNull(posts.firstOrNull { it.third.state != "success" })
    }

    @Test
    fun `a post that fails is retried rather than lost`() {
        failuresLeft = 2
        reporter.observe(record("Success"))
        awaitPosts(1)

        assertEquals("success", posts.single().third.state)
    }
}
