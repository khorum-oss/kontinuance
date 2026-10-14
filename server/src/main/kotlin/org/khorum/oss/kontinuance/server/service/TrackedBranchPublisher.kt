package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Publishes a commit that landed on a binding's tracked branch to the binding's `publish:` target, and
 * says so on the commit as `kontinuance/publish` (043).
 *
 * Publishing belongs to the repository's binding, not to a pipeline: it happens whether or not the
 * delivery that follows succeeds, and a binding with no `publish:` block is never pushed anywhere.
 *
 * A publish is queued, not awaited. A fetch and a push take longer than a webhook sender waits for its
 * answer, and one consumer working through the queue in order means two merges in quick succession reach
 * the remote in the order they happened — the second is a fast-forward of the first, never the reverse.
 */
@Component
class TrackedBranchPublisher(
    private val publisher: CommitPublisher,
    private val reporter: GiteaStatusReporter,
    scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(TrackedBranchPublisher::class.java)
    private val queue = Channel<Pair<RepositoryBinding, String>>(Channel.UNLIMITED)

    init {
        scope.launch { for ((binding, sha) in queue) publish(binding, sha) }
    }

    /** Queues [sha] for publishing if [binding] publishes at all; returns whether it did. */
    fun enqueue(binding: RepositoryBinding, sha: String): Boolean {
        val target = binding.publish ?: return false
        report(binding, sha, "pending", "Publishing to ${target.branch}")
        queue.trySend(binding to sha)
        return true
    }

    // TooGenericExceptionCaught: this is the queue's only consumer. An exception that escaped here would
    // end it, and every later merge would silently go unpublished.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun publish(binding: RepositoryBinding, sha: String) {
        val result = try {
            publisher.publish(binding, sha)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        val target = binding.publish?.let { "${it.remote} ${it.branch}" }
        result.fold(
            onSuccess = {
                log.info("published {}@{} to {}", binding.repo.slug, sha, target)
                report(binding, sha, "success", "Published to ${binding.publish?.branch}")
            },
            onFailure = {
                log.warn("could not publish {}@{} to {}: {}", binding.repo.slug, sha, target, it.message)
                report(binding, sha, "failure", it.message ?: "Publishing failed")
            },
        )
    }

    private fun report(binding: RepositoryBinding, sha: String, state: String, description: String) =
        reporter.report(
            binding.repo,
            sha,
            CommitStatusPoster.Status(state, PUBLISH_CONTEXT, description.take(MAX_DESCRIPTION)),
        )

    companion object {
        /** The publish check's stable name, beside `kontinuance/ci` and `kontinuance/deliver`. */
        const val PUBLISH_CONTEXT = "kontinuance/publish"

        private const val MAX_DESCRIPTION = 140
    }
}
