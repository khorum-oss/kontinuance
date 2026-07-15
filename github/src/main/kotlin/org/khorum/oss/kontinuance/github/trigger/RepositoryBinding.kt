package org.khorum.oss.kontinuance.github.trigger

import org.khorum.oss.kontinuance.github.client.RepoRef
import java.nio.file.Path

/**
 * Configuration binding a repository to the pipeline descriptors Kontinuance runs for it: one for
 * pull-request checks and, optionally, one for delivery on a push to [trackedBranch].
 *
 * @param repo the repository this binding applies to.
 * @param prPipeline descriptor run for pull-request events (the gating check).
 * @param pushPipeline descriptor run for a push to [trackedBranch] (delivery); null disables push delivery.
 * @param trackedBranch the branch whose pushes trigger [pushPipeline] (default `main`).
 */
data class RepositoryBinding(
    val repo: RepoRef,
    val prPipeline: Path,
    val pushPipeline: Path? = null,
    val trackedBranch: String = "main",
)
