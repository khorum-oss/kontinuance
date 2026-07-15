package org.khorum.oss.kontinuance.github.poll

import org.khorum.oss.kontinuance.github.client.GitHubClient
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.khorum.oss.kontinuance.github.trigger.TriggerEvent

/**
 * The poll-based event source: on each [poll], it lists the open pull requests for every configured
 * repository and emits a [TriggerEvent] for each PR whose head SHA is new since the last poll (a newly
 * opened PR, or a new push to an existing one). It records the cursor as it emits, so a subsequent
 * poll of the same head does not re-emit (FR-001, dedup). Outbound only; no inbound exposure.
 *
 * @param client the GitHub seam.
 * @param bindings the configured repositories to watch.
 * @param cursors remembers the last head SHA acted on per PR.
 */
class Poller(
    private val client: GitHubClient,
    private val bindings: List<RepositoryBinding>,
    private val cursors: CursorStore,
) {

    /** Polls every binding once and returns the new PR events observed (recording their cursors). */
    suspend fun poll(): List<TriggerEvent> {
        val events = mutableListOf<TriggerEvent>()
        for (binding in bindings) {
            for (pr in client.listOpenPullRequests(binding.repo)) {
                val key = "${binding.repo.slug}#pr-${pr.number}"
                if (cursors.lastSeen(key) != pr.headSha) {
                    events += TriggerEvent(
                        repo = binding.repo,
                        sha = pr.headSha,
                        kind = TriggerEvent.Kind.PULL_REQUEST,
                        ref = pr.headRef,
                        pullNumber = pr.number,
                    )
                    cursors.record(key, pr.headSha)
                }
            }
        }
        return events
    }
}
