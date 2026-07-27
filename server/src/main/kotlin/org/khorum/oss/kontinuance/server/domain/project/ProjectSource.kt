package org.khorum.oss.kontinuance.server.domain.project

/**
 * A project's optional **source** (033): the repo URL and branch a run of the project checks out. Stored
 * beside the project's descriptor (as a `<name>.meta.json` sidecar) and applied to the pipeline at trigger
 * time by [ProjectSourceInjector]. A blank [repo] means "no
 * source" — the descriptor's own checkout is used unchanged.
 *
 * @param repo the repository URL to clone; blank/absent means no source.
 * @param branch the branch/tag to check out (the engine's `ref`); `null` clones the default.
 */
data class ProjectSource(
    val repo: String? = null,
    val branch: String? = null,
) {
    /** Whether this source actually points at a repo (a non-blank URL). */
    val hasRepo: Boolean get() = !repo.isNullOrBlank()
}
