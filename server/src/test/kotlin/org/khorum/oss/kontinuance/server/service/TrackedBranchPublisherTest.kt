package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.trigger.PublishTarget
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackedBranchPublisherTest {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val posts = CopyOnWriteArrayList<Pair<String, CommitStatusPoster.Status>>()
    private val published = CopyOnWriteArrayList<String>()
    private var outcome: (String) -> Result<Unit> = { Result.success(Unit) }

    private val reporter = GiteaStatusReporter(
        poster = { _, sha, status -> posts += sha to status; Result.success(Unit) },
        scope = scope,
        runUrlBase = "",
    )
    private val publisher = TrackedBranchPublisher(
        publisher = { _, sha ->
            delay(if (sha == FIRST) 100 else 0)
            published += sha
            outcome(sha)
        },
        reporter = reporter,
        scope = scope,
    )

    private val binding = RepositoryBinding(
        repo = RepoRef("khorum-oss", "relikquary"),
        prPipeline = Path.of("/pr.yaml"),
        publish = PublishTarget("https://github.com/khorum-oss/relikquary.git", "main"),
    )

    @AfterEach
    fun stop() = scope.cancel()

    private fun awaitPosts(count: Int) = runBlocking {
        withTimeout(10_000) { while (posts.size < count) delay(10) }
    }

    @Test
    fun `a publish is pending, then success, as kontinuance-publish on the commit`() {
        assertTrue(publisher.enqueue(binding, FIRST))
        awaitPosts(2)

        assertEquals(listOf(FIRST to "pending", FIRST to "success"), posts.map { it.first to it.second.state })
        assertEquals(setOf("kontinuance/publish"), posts.map { it.second.context }.toSet())
    }

    @Test
    fun `a refused push is a failure that says why`() {
        outcome = { Result.failure(IllegalStateException("the remote refused the push to main: ! [rejected]")) }
        publisher.enqueue(binding, FIRST)
        awaitPosts(2)

        assertEquals("failure", posts[1].second.state)
        assertEquals("the remote refused the push to main: ! [rejected]", posts[1].second.description)
    }

    @Test
    fun `merges are published in the order they arrived, and a publisher that throws does not stop the next`() {
        outcome = { if (it == FIRST) error("boom") else Result.success(Unit) }
        publisher.enqueue(binding, FIRST)
        publisher.enqueue(binding, SECOND)
        awaitPosts(4)

        assertEquals(listOf(FIRST, SECOND), published)
        assertEquals(listOf("failure", "success"), posts.filter { it.second.state != "pending" }.map { it.second.state })
    }

    @Test
    fun `a binding without a publish block is never published and posts nothing`() {
        assertFalse(publisher.enqueue(binding.copy(publish = null), FIRST))
        assertTrue(publisher.enqueue(binding, SECOND))
        awaitPosts(2)

        assertEquals(listOf(SECOND), published)
        assertEquals(setOf(SECOND), posts.map { it.first }.toSet())
    }

    private companion object {
        val FIRST = "a".repeat(40)
        val SECOND = "b".repeat(40)
    }
}
