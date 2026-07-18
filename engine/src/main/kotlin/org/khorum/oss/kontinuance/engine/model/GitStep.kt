package org.khorum.oss.kontinuance.engine.model

/**
 * Checks out a git repository into the run's shared workspace, so the following steps build the
 * checked-out source. Executed by
 * [org.khorum.oss.kontinuance.engine.execution.steps.GitStepExecutor] through the shared
 * `ProcessBuilder` path (a `git clone`), inheriting the same isolation, masking, and status lifecycle as
 * every other step type.
 *
 * @param url the repository URL to clone (non-empty).
 * @param ref an optional branch or tag to clone (a shallow, branch-scoped clone); an arbitrary commit SHA
 *   is not yet supported.
 * @param dir the target sub-directory in the workspace; the default `"."` clones into the workspace root
 *   and so requires it to be empty (put the checkout first, or use a sub-directory).
 * @param depth the shallow clone depth; `null` for a full clone.
 */
data class GitStep(
    val url: String,
    val ref: String? = null,
    val dir: String = ".",
    val depth: Int? = 1,
) : StepDefinition {
    init {
        require(url.isNotBlank()) { "GitStep requires a url" }
        require(depth == null || depth > 0) { "GitStep depth must be > 0 when set, was $depth" }
    }
}
