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
 * @param cloneBase the host root the repository is cloned from (default GitHub). A binding whose source
 *   of truth is another host — a self-hosted Gitea — names it here, so the code a run builds comes from
 *   the same place the event did (043).
 */
data class RepositoryBinding(
    val repo: RepoRef,
    val prPipeline: Path,
    val pushPipeline: Path? = null,
    val trackedBranch: String = "main",
    val cloneBase: String = DEFAULT_CLONE_BASE,
) {
    /** The URL a run checks this repository out from. */
    val cloneUrl: String get() = "${cloneBase.trimEnd('/')}/${repo.slug}.git"

    companion object {
        const val DEFAULT_CLONE_BASE = "https://github.com"
    }
}
