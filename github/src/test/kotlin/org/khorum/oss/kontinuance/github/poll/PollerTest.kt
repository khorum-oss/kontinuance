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
}
