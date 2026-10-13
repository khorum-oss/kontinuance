package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.report.KONTINUANCE_CHECK_CONTEXT
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Reports webhook-started runs back to Gitea as commit statuses (043): `pending` while a run is in
 * flight, then its verdict. That status is what Gitea's branch protection requires before a merge.
 *
 * It watches the run store rather than being called by whoever started the run. A run reaches its end by
 * more than one road — straight through, or paused at an approval gate and resumed later by a different
 * request — and every road ends in a write to the store. Watching the writes cannot miss one.
 *
 * Only runs recorded with a webhook trigger are reported. A manual dispatch and a dashboard run stay
 * silent, as they always have: nothing asked Gitea to expect a status for them.
 *
 * Statuses are posted by a single consumer, in the order the writes happened, so a slow `pending` can
 * never land after the verdict and leave a finished run looking in-progress. Reporting is observability:
 * a status that cannot be posted is retried, then logged, and never fails the run.
 */
@Component
class GiteaStatusReporter(
    private val poster: CommitStatusPoster,
    scope: CoroutineScope,
    @param:Value("\${kontinuance.hooks.gitea.run-url-base:}") private val runUrlBase: String,
) {
    private val log = LoggerFactory.getLogger(GiteaStatusReporter::class.java)
    private val queue = Channel<Pending>(Channel.UNLIMITED)

    // The last state posted per run. The engine rewrites a running record on every step transition, and
    // each of those would otherwise post another identical `pending`.
    private val posted = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > REMEMBERED
    }

    init {
        scope.launch { for (item in queue) deliver(item) }
    }

    /** Called for every run-store write. Cheap and non-blocking: the posting happens on the consumer. */
    fun observe(record: RunRecord) {
        val context = CONTEXTS[record.trigger] ?: return
        val (owner, name) = record.repo?.split('/')?.takeIf { it.size == 2 } ?: return
        val sha = record.sha ?: return
        val (state, description) = describe(record)
        synchronized(posted) {
            if (posted[record.id] == state) return
            posted[record.id] = state
        }
        val url = runUrlBase.takeIf { it.isNotBlank() }?.let { "${it.trimEnd('/')}/runs/${record.id}" }
        queue.trySend(Pending(RepoRef(owner, name), sha, CommitStatusPoster.Status(state, context, description, url)))
    }

    private suspend fun deliver(item: Pending) {
        repeat(ATTEMPTS) { attempt ->
            val result = poster.post(item.repo, item.sha, item.status)
            if (result.isSuccess) return
            if (attempt == ATTEMPTS - 1) {
                log.warn(
                    "could not report {} '{}' on {}@{}: {}",
                    item.status.context, item.status.state, item.repo.slug, item.sha, result.exceptionOrNull()?.message,
                )
            } else {
                delay(BACKOFF_MILLIS shl attempt)
            }
        }
    }

    private fun describe(record: RunRecord): Pair<String, String> = when (record.status) {
        "Running" -> "pending" to "Run in progress"
        "WaitingOnApproval" -> "pending" to "Waiting on approval"
        "Success" -> "success" to "All stages passed"
        "Failed" -> "failure" to ("Run failed" + record.reason?.let { ": $it" }.orEmpty()).take(MAX_DESCRIPTION)
        "TimedOut" -> "failure" to "A step timed out"
        "Cancelled" -> "error" to "Run cancelled"
        else -> "error" to "Run ended in an unexpected state: ${record.status}"
    }

    private data class Pending(val repo: RepoRef, val sha: String, val status: CommitStatusPoster.Status)

    companion object {
        /** The delivery check's stable name; like [KONTINUANCE_CHECK_CONTEXT], it must not change silently. */
        const val DELIVER_CONTEXT = "kontinuance/deliver"

        private val CONTEXTS = mapOf(
            GiteaWebhookReceiver.PR_TRIGGER to KONTINUANCE_CHECK_CONTEXT,
            GiteaWebhookReceiver.PUSH_TRIGGER to DELIVER_CONTEXT,
        )
        private const val ATTEMPTS = 4
        private const val BACKOFF_MILLIS = 500L
        private const val MAX_DESCRIPTION = 140
        private const val REMEMBERED = 500
    }
}
