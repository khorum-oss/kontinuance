package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.domain.ci.CiBindings
import org.khorum.oss.kontinuance.server.domain.ci.CiDispatchRequest
import org.khorum.oss.kontinuance.server.domain.ci.CiEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.io.path.readText

/**
 * Catches up, once, on a merge whose webhook was missed while the server was down (043).
 *
 * Polling healed itself: whatever changed while it was stopped was found on the next cycle. A webhook
 * does not — Gitea called, nobody answered, and the merge is simply never delivered. So at start-up, for
 * each binding, the tracked branch's head is asked of Gitea and:
 *
 * - **delivered** if no run of the binding's push pipeline has succeeded at that commit, through the same
 *   [CiDispatcher] a delivery would use, so the allow-list and the in-flight guard apply unchanged;
 * - **published** if the binding has a `publish:` block. Publishing a commit already on the remote is a
 *   no-op, and asking the remote first would be a second network call to learn nothing more.
 *
 * It runs once and never again: it covers the case that actually loses events, and it is not a poll
 * loop. Pull requests are not reconciled — a missed one has no `kontinuance/ci` status, so it cannot
 * merge, which is loud already; re-checking every open one on each restart would cost a build each.
 *
 * `kontinuance.hooks.gitea.reconcile-on-start: false` leaves it out of the context entirely.
 */
@Component
@ConditionalOnProperty("kontinuance.hooks.gitea.reconcile-on-start", havingValue = "true", matchIfMissing = true)
class GiteaReconciler(
    private val bindings: CiBindings,
    private val gitea: GiteaApi,
    private val store: RunStore,
    private val dispatcher: CiDispatcher,
    private val publisher: TrackedBranchPublisher,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(GiteaReconciler::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (!gitea.configured) return
        scope.launch { reconcile() }
    }

    /** One pass over every binding; one binding failing never stops the rest. */
    suspend fun reconcile() {
        val configured = bindings.load().getOrElse {
            log.warn("start-up reconcile skipped: {}", it.message)
            return
        }
        configured.filter { it.pushPipeline != null || it.publish != null }.forEach { reconcile(it) }
    }

    private suspend fun reconcile(binding: RepositoryBinding) {
        val slug = binding.repo.slug
        val head = gitea.head(binding.repo, binding.trackedBranch).getOrElse {
            log.warn("start-up reconcile: cannot read the head of {} {}: {}", slug, binding.trackedBranch, it.message)
            return
        }
        publisher.enqueue(binding, head)
        val descriptor = binding.pushPipeline ?: return

        // A record names its pipeline, not the file it came from, so the descriptor is read for its name.
        // Matching the pipeline rather than the trigger counts a delivery however it was started.
        val pipeline = runCatching { PipelineDescriptor.parse(descriptor.readText()).name }.getOrElse {
            log.warn("start-up reconcile: cannot read {}: {}", descriptor, it.message)
            return
        }
        val delivered = store.recent(HISTORY).any {
            it.repo.equals(slug, ignoreCase = true) && it.sha == head && it.pipeline == pipeline && it.status == "Success"
        }
        if (delivered) return

        val request = CiDispatchRequest(repo = slug, sha = head, event = CiEvent.PUSH)
        when (val result = dispatcher.dispatch(request, RECONCILE_TRIGGER)) {
            is CiDispatcher.Result.Accepted -> log.info("start-up reconcile: delivering {}@{} as {}", slug, head, result.id)
            is CiDispatcher.Result.Existing -> log.info("start-up reconcile: {}@{} is already running as {}", slug, head, result.id)
            is CiDispatcher.Result.Rejected -> log.warn("start-up reconcile: {}@{} refused: {}", slug, head, result.reason)
        }
    }

    companion object {
        /** How a delivery started by the reconcile is recorded; it reports as `kontinuance/deliver`. */
        const val RECONCILE_TRIGGER = "gitea-reconcile"

        /** How far back a successful delivery is looked for. */
        private const val HISTORY = 500
    }
}
