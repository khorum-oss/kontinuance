package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.engine.model.GitStep
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.server.projects.ProjectSource

/**
 * Applies a project's [ProjectSource] (033) to a parsed [Pipeline] so a triggered run checks out the
 * project's repo/branch rather than whatever the descriptor's `git:` step names:
 *
 * - **Override** — if the pipeline already has a checkout, the **first** [GitStep] has its `url` replaced by
 *   the source repo (and its `ref` replaced by the branch when one is set; otherwise the descriptor's `ref`
 *   is kept). Later `git:` steps are left alone.
 * - **Synthesize** — if the pipeline has no `git:` step, a checkout stage of the source is prepended so the
 *   source is present for the following steps. Its stage name is made unique against the existing stages.
 * - **No-op** — a source without a repo returns the pipeline unchanged (the descriptor is used as-is).
 *
 * Pure model→model transform (immutable `.copy`), so it needs no Spring and is exhaustively unit-testable.
 */
object ProjectSourceInjector {

    fun apply(pipeline: Pipeline, source: ProjectSource?): Pipeline {
        if (source == null || !source.hasRepo) return pipeline
        val repo = source.repo!!
        val branch = source.branch?.takeIf { it.isNotBlank() }

        val overridden = overrideFirstCheckout(pipeline, repo, branch)
        if (overridden != null) return overridden

        val checkout = Stage(
            name = uniqueStageName(pipeline.stages.map { it.name }, "checkout"),
            steps = listOf(Step(name = "checkout", definition = GitStep(url = repo, ref = branch))),
        )
        return pipeline.copy(stages = listOf(checkout) + pipeline.stages)
    }

    // Rewrite the url/ref of the first GitStep found, in order; null when the pipeline has no checkout.
    private fun overrideFirstCheckout(pipeline: Pipeline, repo: String, branch: String?): Pipeline? {
        pipeline.stages.forEachIndexed { si, stage ->
            stage.steps.forEachIndexed { pi, step ->
                val git = step.definition
                if (git is GitStep) {
                    val newStep = step.copy(definition = git.copy(url = repo, ref = branch ?: git.ref))
                    val newSteps = stage.steps.toMutableList().apply { this[pi] = newStep }
                    val newStages = pipeline.stages.toMutableList().apply { this[si] = stage.copy(steps = newSteps) }
                    return pipeline.copy(stages = newStages)
                }
            }
        }
        return null
    }

    private fun uniqueStageName(existing: List<String>, base: String): String {
        if (base !in existing) return base
        var n = 2
        while ("$base-$n" in existing) n++
        return "$base-$n"
    }
}
