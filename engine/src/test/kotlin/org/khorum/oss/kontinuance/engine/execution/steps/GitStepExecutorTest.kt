package org.khorum.oss.kontinuance.engine.execution.steps

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.GitStep
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class GitStepExecutorTest {

    @Test
    fun `builds the git clone argv with depth, branch, url, and dir`() {
        val argv = GitStepExecutor.argv(
            GitStep(url = "https://example.com/r.git", ref = "main", dir = "src", depth = 1),
        )
        assertEquals(
            listOf("git", "clone", "--depth", "1", "--branch", "main", "https://example.com/r.git", "src"),
            argv,
        )
    }

    @Test
    fun `omits depth and branch when unset, defaulting dir to dot`() {
        val argv = GitStepExecutor.argv(GitStep(url = "u", depth = null))
        assertEquals(listOf("git", "clone", "u", "."), argv)
    }

    @Test
    fun `clones a repo into the shared workspace so a later step sees the source`(@TempDir src: Path) =
        runBlocking {
            assumeTrue(onPath("git"), "git is not installed; skipping the real-clone test")
            seedRepo(src)

            val pipeline = Pipeline(
                "checkout-demo",
                listOf(
                    // Clone into the (empty) workspace root, then confirm the committed file is present.
                    Stage("checkout", listOf(Step("clone", GitStep(url = src.toUri().toString(), depth = 1)))),
                    Stage("verify", listOf(Step("present", RunStep("test -f README.md")))),
                ),
            )

            val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)

            assertEquals(
                PipelineStatus.Success,
                run.status,
                "the checked-out file should be visible to a later step",
            )
        }

    /** Creates a one-commit git repo at [dir] with a README the clone test looks for. */
    private fun seedRepo(dir: Path) {
        Files.writeString(dir.resolve("README.md"), "hello\n")
        git(dir, "init", "-q")
        git(dir, "add", "README.md")
        git(dir, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "-m", "init")
    }

    private fun git(dir: Path, vararg args: String) {
        val exit = ProcessBuilder(listOf("git", "-C", dir.toString()) + args)
            .redirectErrorStream(true)
            .start()
            .waitFor()
        check(exit == 0) { "git ${args.joinToString(" ")} failed ($exit)" }
    }

    private fun onPath(binary: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(File.pathSeparator).any { dir -> Files.isExecutable(Path.of(dir, binary)) }
    }
}
