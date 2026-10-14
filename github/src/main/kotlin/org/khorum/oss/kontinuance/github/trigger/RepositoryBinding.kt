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
 * @param publish where a push to [trackedBranch] is also published; null publishes nothing (043).
 */
data class RepositoryBinding(
    val repo: RepoRef,
    val prPipeline: Path,
    val pushPipeline: Path? = null,
    val trackedBranch: String = "main",
    val cloneBase: String = DEFAULT_CLONE_BASE,
    val publish: PublishTarget? = null,
) {
    /** The URL a run checks this repository out from. */
    val cloneUrl: String get() = "${cloneBase.trimEnd('/')}/${repo.slug}.git"

    companion object {
        const val DEFAULT_CLONE_BASE = "https://github.com"
    }
}

/**
 * Where a commit that lands on a binding's tracked branch is also pushed (043): one [remote], one
 * [branch]. Nothing else about the repository is published — a private source's other branches and its
 * pull-request heads never leave it.
 *
 * @param remote the git URL pushed to. It never carries a credential; the host supplies one separately.
 * @param branch the branch on [remote] the commit is pushed to.
 */
data class PublishTarget(val remote: String, val branch: String) {
    init {
        require(!USERINFO.containsMatchIn(remote)) { "publish.remote must not embed a credential" }
    }

    private companion object {
        val USERINFO = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]*@")
    }
}
