package org.khorum.oss.kontinuance.github.poll

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.client.PullRequest
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.support.RecordingGitHubClient
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.khorum.oss.kontinuance.github.trigger.TriggerEvent
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PollerTest {

    private val repo = RepoRef("khorum-oss", "kontinuance")
    private val binding = RepositoryBinding(repo, Path.of("pr.yaml"))

    private fun poller(pulls: List<PullRequest>, cursors: CursorStore) =
        Poller(RecordingGitHubClient(pulls), listOf(binding), cursors)

    @Test
    fun `a new PR head emits a pull-request event and records the cursor`() = runTest {
        val cursors = InMemoryCursorStore()
        val events = poller(listOf(PullRequest(7, "sha-a", "feature", "main")), cursors).poll()

        val event = events.single()
        assertEquals(TriggerEvent.Kind.PULL_REQUEST, event.kind)
        assertEquals("sha-a", event.sha)
        assertEquals(7, event.pullNumber)
        assertEquals("sha-a", cursors.lastSeen("${repo.slug}#pr-7"))
    }

    @Test
    fun `an already-seen head is not re-emitted`() = runTest {
        val cursors = InMemoryCursorStore()
        val pulls = listOf(PullRequest(7, "sha-a", "feature", "main"))

        assertEquals(1, poller(pulls, cursors).poll().size)
        assertTrue(poller(pulls, cursors).poll().isEmpty(), "the same head must dedup on re-poll")
    }

    @Test
    fun `a new push to an existing PR (new head SHA) emits a fresh event`() = runTest {
        val cursors = InMemoryCursorStore()
        poller(listOf(PullRequest(7, "sha-a", "feature", "main")), cursors).poll()

        val events = poller(listOf(PullRequest(7, "sha-b", "feature", "main")), cursors).poll()
        assertEquals("sha-b", events.single().sha)
    }

    @Test
    fun `a tracked-branch advance emits a push event when a delivery pipeline is configured`() = runTest {
        val cursors = InMemoryCursorStore()
        val delivery = RepositoryBinding(repo, Path.of("pr.yaml"), Path.of("deliver.yaml"), trackedBranch = "main")
        val client = RecordingGitHubClient(branchHeads = mapOf("main" to "push-sha"))

        val events = Poller(client, listOf(delivery), cursors).poll()

        val push = events.single { it.kind == TriggerEvent.Kind.PUSH }
        assertEquals("push-sha", push.sha)
        assertEquals("main", push.ref)
    }

    @Test
    fun `a tracked-branch head is not re-emitted, and no push event without a delivery pipeline`() = runTest {
        val cursors = InMemoryCursorStore()
        val client = RecordingGitHubClient(branchHeads = mapOf("main" to "push-sha"))

        // No pushPipeline configured -> no push event at all.
        val prOnly = Poller(client, listOf(RepositoryBinding(repo, Path.of("pr.yaml"))), cursors).poll()
        assertTrue(prOnly.none { it.kind == TriggerEvent.Kind.PUSH })

        // With a delivery pipeline, the same head dedups on re-poll.
        val delivery = RepositoryBinding(repo, Path.of("pr.yaml"), Path.of("deliver.yaml"))
        assertEquals(1, Poller(client, listOf(delivery), cursors).poll().count { it.kind == TriggerEvent.Kind.PUSH })
        assertTrue(Poller(client, listOf(delivery), cursors).poll().none { it.kind == TriggerEvent.Kind.PUSH })
    }
}
