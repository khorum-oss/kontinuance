package org.khorum.oss.kontinuance.github.trigger

import org.khorum.oss.kontinuance.github.client.RepoRef
import java.nio.file.Path

/**
 * Resolves an observed [TriggerEvent] to the pipeline descriptor that should run for it, from the
 * configured [RepositoryBinding]s. An event for an unconfigured repo — or a push to a non-tracked
 * branch, or a push with no delivery pipeline — resolves to `null` (ignored, no status posted).
 */
class TriggerResolver(bindings: List<RepositoryBinding>) {

    private val byRepo: Map<RepoRef, RepositoryBinding> = bindings.associateBy { it.repo }

    /** The descriptor to run for [event], or `null` when nothing is configured for it. */
    fun resolve(event: TriggerEvent): Path? {
        val binding = byRepo[event.repo] ?: return null
        return when (event.kind) {
            TriggerEvent.Kind.PULL_REQUEST -> binding.prPipeline
            TriggerEvent.Kind.MANUAL -> binding.prPipeline
            TriggerEvent.Kind.PUSH ->
                if (event.ref == binding.trackedBranch) binding.pushPipeline else null
        }
    }
}
