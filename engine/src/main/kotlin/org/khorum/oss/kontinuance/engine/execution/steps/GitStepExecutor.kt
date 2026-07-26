package org.khorum.oss.kontinuance.engine.execution.steps

import org.khorum.oss.kontinuance.engine.execution.DockerStepSandbox
import org.khorum.oss.kontinuance.engine.execution.ProcessStepExecutor
import org.khorum.oss.kontinuance.engine.execution.StepSandbox
import org.khorum.oss.kontinuance.engine.model.GitStep
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import java.nio.file.Path

/**
 * Runs a [GitStep] into the run's shared workspace. Without a [GitStep.sha] it launches `git clone` (the
 * branch/tag path); with a SHA it fetches that commit and checks it out (034), since `git clone` cannot name
 * a commit. A missing `git` binary or an unreachable repository/commit surfaces as a FAILED step (via the
 * shared [ProcessStepExecutor] launch handling), not an exception. The clone argv is passed directly (no
 * shell); the SHA argv uses `sh -c` but passes the URL/SHA/dir as **positional parameters**, so they are
 * never interpolated into the command and not subject to shell injection.
 */
class GitStepExecutor(sandbox: StepSandbox = DockerStepSandbox()) : ProcessStepExecutor(sandbox = sandbox) {

    override fun supports(definition: StepDefinition): Boolean = definition is GitStep

    override fun command(step: Step, workingDir: Path): List<String> {
        val git = step.definition as GitStep
        return if (git.sha != null) shaArgv(git) else argv(git)
    }

    companion object {
        /** `git clone [--depth d] [--branch ref] <url> <dir>`. Pure (branch/tag path). */
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

        /**
         * Fetch-then-checkout for a pinned commit (034): init the target dir, fetch the exact commit
         * (honoring `depth` where the remote allows a by-id fetch), and check out `FETCH_HEAD`. Pure. The
         * URL/SHA/dir are shell **positional parameters** (`$1`/`$2`/`$3`), so nothing is interpolated.
         */
        fun shaArgv(git: GitStep): List<String> {
            val depthOpt = git.depth?.let { "--depth $it " } ?: ""
            val script = buildString {
                append("set -e; ")
                append("mkdir -p \"\$3\"; ")
                append("git -C \"\$3\" init -q; ")
                append("git -C \"\$3\" remote add origin \"\$1\"; ")
                append("git -C \"\$3\" fetch -q ${depthOpt}origin \"\$2\"; ")
                append("git -C \"\$3\" checkout -q FETCH_HEAD")
            }
            return listOf("sh", "-c", script, "sh", git.url, git.sha!!, git.dir)
        }
    }
}
