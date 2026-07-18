package org.khorum.oss.kontinuance.engine.execution.steps

import org.khorum.oss.kontinuance.engine.execution.ProcessStepExecutor
import org.khorum.oss.kontinuance.engine.model.GitStep
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import java.nio.file.Path

/**
 * Runs a [GitStep] by launching `git clone` into the run's shared workspace. A missing `git` binary or an
 * unreachable repository surfaces as a FAILED step naming `git` (via the shared [ProcessStepExecutor]
 * launch handling), not an exception. The argv is passed directly (no shell), so the URL/ref are not
 * subject to shell interpretation.
 */
class GitStepExecutor : ProcessStepExecutor() {

    override fun supports(definition: StepDefinition): Boolean = definition is GitStep

    override fun command(step: Step, workingDir: Path): List<String> = argv(step.definition as GitStep)

    companion object {
        /** `git clone [--depth d] [--branch ref] <url> <dir>`. Pure. */
        fun argv(git: GitStep): List<String> = buildList {
            add("git")
            add("clone")
            git.depth?.let {
                add("--depth")
                add(it.toString())
            }
            git.ref?.let {
                add("--branch")
                add(it)
            }
            add(git.url)
            add(git.dir)
        }
    }
}
