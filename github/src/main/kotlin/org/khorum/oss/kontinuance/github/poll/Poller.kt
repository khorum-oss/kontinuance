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
 * Pull requests whose head lives in a **different** repository (forks) are skipped entirely — see
 * [pollPullRequests].
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

    /**
     * Polls every binding once and returns the new events observed (recording their cursors): a
     * [TriggerEvent.Kind.PULL_REQUEST] for each open PR whose head SHA is new, and — for bindings with
     * a delivery pipeline — a [TriggerEvent.Kind.PUSH] when the tracked branch's head advances.
     */
    suspend fun poll(): List<TriggerEvent> {
        val events = mutableListOf<TriggerEvent>()
        for (binding in bindings) {
            events += pollPullRequests(binding)
            events += pollTrackedBranch(binding)
        }
        return events
    }

    private suspend fun pollPullRequests(binding: RepositoryBinding): List<TriggerEvent> =
        client.listOpenPullRequests(binding.repo).mapNotNull { pr ->
            // A fork PR is never built here. The runner is a host carrying the operator's toolchain,
            // registry credentials and LAN access; a PR from a fork is arbitrary code from a stranger, and
            // on a public repository anyone can open one. Deliberately records NO cursor: skipping is not
            // "handled", so the decision is re-made every poll rather than being remembered as done.
            if (!pr.headRepo.equals(binding.repo.slug, ignoreCase = true)) return@mapNotNull null
            val key = "${binding.repo.slug}#pr-${pr.number}"
            if (cursors.lastSeen(key) == pr.headSha) return@mapNotNull null
            cursors.record(key, pr.headSha)
            TriggerEvent(binding.repo, pr.headSha, TriggerEvent.Kind.PULL_REQUEST, pr.headRef, pr.number)
        }

    private suspend fun pollTrackedBranch(binding: RepositoryBinding): List<TriggerEvent> {
        if (binding.pushPipeline == null) return emptyList()
        val head = client.branchHead(binding.repo, binding.trackedBranch) ?: return emptyList()
        val key = "${binding.repo.slug}#push-${binding.trackedBranch}"
        if (cursors.lastSeen(key) == head) return emptyList()
        cursors.record(key, head)
        return listOf(TriggerEvent(binding.repo, head, TriggerEvent.Kind.PUSH, binding.trackedBranch))
    }
}
