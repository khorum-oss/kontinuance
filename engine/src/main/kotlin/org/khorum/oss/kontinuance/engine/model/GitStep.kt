package org.khorum.oss.kontinuance.engine.model

/**
 * Checks out a git repository into the run's shared workspace, so the following steps build the
 * checked-out source. Executed by
 * [org.khorum.oss.kontinuance.engine.execution.steps.GitStepExecutor] through the shared
 * `ProcessBuilder` path (a `git clone`), inheriting the same isolation, masking, and status lifecycle as
 * every other step type.
 *
 * @param url the repository URL to clone (non-empty).
 * @param ref an optional branch or tag to clone (a shallow, branch-scoped clone). Mutually exclusive with
 *   [sha]; to pin an exact commit use [sha].
 * @param sha an optional exact commit SHA to check out (034). Unlike [ref], a SHA cannot be named to
 *   `git clone`, so it is realized by fetching the commit and checking it out. Mutually exclusive with [ref].
 * @param dir the target sub-directory in the workspace; the default `"."` clones into the workspace root
 *   and so requires it to be empty (put the checkout first, or use a sub-directory).
 * @param depth the shallow clone depth; `null` for a full clone.
 */
data class GitStep(
    val url: String,
    val ref: String? = null,
    val sha: String? = null,
    val dir: String = ".",
    val depth: Int? = 1,
) : StepDefinition {
    init {
        require(url.isNotBlank()) { "GitStep requires a url" }
        require(depth == null || depth > 0) { "GitStep depth must be > 0 when set, was $depth" }
        require(!(ref != null && sha != null)) { "GitStep cannot set both ref and sha (they are mutually exclusive)" }
        sha?.let { require(it.isNotBlank()) { "GitStep sha must be non-blank when set" } }
    }
}
