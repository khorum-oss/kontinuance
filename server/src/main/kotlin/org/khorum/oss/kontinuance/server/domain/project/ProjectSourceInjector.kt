package org.khorum.oss.kontinuance.server.domain.project

import org.khorum.oss.kontinuance.engine.model.GitStep
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step

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
 * A source value that looks like a commit SHA (hex, 7–40 chars) pins [GitStep.sha]; any other value is a
 * branch/tag `ref` (034) — so the project's single "source" field serves both.
 *
 * Pure model→model transform (immutable `.copy`), so it needs no Spring and is exhaustively unit-testable.
 */
object ProjectSourceInjector {

    private val SHA = Regex("[0-9a-fA-F]{7,40}")

    fun apply(pipeline: Pipeline, source: ProjectSource?): Pipeline {
        if (source == null || !source.hasRepo) return pipeline
        val repo = source.repo!!
        val value = source.branch?.takeIf { it.isNotBlank() }

        val overridden = overrideFirstCheckout(pipeline, repo, value)
        if (overridden != null) return overridden

        val checkout = Stage(
            name = uniqueStageName(pipeline.stages.map { it.name }, "checkout"),
            steps = listOf(Step(name = "checkout", definition = sourceCheckout(repo, value))),
        )
        return pipeline.copy(stages = listOf(checkout) + pipeline.stages)
    }

    // A fresh checkout of the source: a SHA value pins `sha`, any other value is a branch/tag `ref`.
    private fun sourceCheckout(repo: String, value: String?): GitStep = when {
        value == null -> GitStep(url = repo)
        SHA.matches(value) -> GitStep(url = repo, sha = value)
        else -> GitStep(url = repo, ref = value)
    }

    // Rewrite the url + ref/sha of the first GitStep found, in order; null when the pipeline has no checkout.
    private fun overrideFirstCheckout(pipeline: Pipeline, repo: String, value: String?): Pipeline? {
        pipeline.stages.forEachIndexed { si, stage ->
            stage.steps.forEachIndexed { pi, step ->
                val git = step.definition
                if (git is GitStep) {
                    val newStep = step.copy(definition = overrideSource(git, repo, value))
                    val newSteps = stage.steps.toMutableList().apply { this[pi] = newStep }
                    val newStages = pipeline.stages.toMutableList().apply { this[si] = stage.copy(steps = newSteps) }
                    return pipeline.copy(stages = newStages)
                }
            }
        }
        return null
    }

    // Override a descriptor git step's url + what it checks out. A blank value keeps the descriptor's ref/sha;
    // a SHA value pins `sha` (clearing `ref`); any other value sets `ref` (clearing `sha`).
    private fun overrideSource(git: GitStep, repo: String, value: String?): GitStep = when {
        value == null -> git.copy(url = repo)
        SHA.matches(value) -> git.copy(url = repo, ref = null, sha = value)
        else -> git.copy(url = repo, ref = value, sha = null)
    }

    private fun uniqueStageName(existing: List<String>, base: String): String {
        if (base !in existing) return base
        var n = 2
        while ("$base-$n" in existing) n++
        return "$base-$n"
    }
}
