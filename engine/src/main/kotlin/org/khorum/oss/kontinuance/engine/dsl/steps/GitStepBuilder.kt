package org.khorum.oss.kontinuance.engine.dsl.steps

import org.khorum.oss.kontinuance.engine.dsl.KontinuanceDsl
import org.khorum.oss.kontinuance.engine.model.GitStep
import org.khorum.oss.kontinuance.engine.model.StepDslBuilder

/**
 * Builder for the git-checkout configuration of a [gitStep]. Yields the same [GitStep] model as the
 * descriptor's `git:` key (Principle I).
 */
@KontinuanceDsl
class GitStepBuilder {
    /** Repository URL to clone (required). */
    var url: String = ""

    /** Optional branch or tag to clone. */
    var ref: String? = null

    /** Target sub-directory in the workspace (default `"."`, the workspace root). */
    var dir: String = "."

    /** Shallow clone depth; `null` for a full clone. */
    var depth: Int? = 1

    internal fun build(): GitStep = GitStep(url = url, ref = ref, dir = dir, depth = depth)
}

/**
 * Declares a git-checkout step named [name] inside a `steps { }` block — the typed counterpart to the
 * descriptor's `git:` key. Clones the repository into the run's shared workspace so later steps build the
 * checked-out source. [options] carries the shared step envelope (`timeout`/`enabled`/`secrets`/
 * `workingDir`), identical to the v0 `step { }`.
 */
fun StepDslBuilder.Group.gitStep(
    name: String,
    options: TypedStepOptions = TypedStepOptions(),
    block: GitStepBuilder.() -> Unit,
) {
    val definition = GitStepBuilder().apply(block).build()
    step { configureStep(name, definition, options) }
}
