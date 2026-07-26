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
import kotlin.test.assertTrue

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

    @Test
    fun `builds the fetch-then-checkout argv for a pinned commit, passing url-sha-dir as positional params`() {
        val argv = GitStepExecutor.shaArgv(GitStep(url = "https://example.com/r.git", sha = "abc1234", dir = "src", depth = 1))
        assertEquals("sh", argv[0])
        assertEquals("-c", argv[1])
        // the url/sha/dir are positional args after the `sh` argv[0]-placeholder, never interpolated
        assertEquals(listOf("sh", "https://example.com/r.git", "abc1234", "src"), argv.subList(3, 7))
        assertTrue(argv[2].contains("fetch"), "the script fetches the commit")
        assertTrue(argv[2].contains("checkout -q FETCH_HEAD"), "the script checks out the fetched commit")
        assertTrue(argv[2].contains("--depth 1"), "honors the shallow depth")
    }

    @Test
    fun `checks out an exact commit SHA into the workspace, not the branch tip`(@TempDir src: Path) =
        runBlocking {
            assumeTrue(onPath("git"), "git is not installed; skipping the real-checkout test")
            val firstSha = seedTwoCommitRepo(src)

            val pipeline = Pipeline(
                "sha-demo",
                listOf(
                    // Pin to the FIRST commit: README exists, but SECOND.md (added by the second commit) must not.
                    Stage("checkout", listOf(Step("clone", GitStep(url = src.toUri().toString(), sha = firstSha, depth = null)))),
                    Stage("verify", listOf(Step("present", RunStep("test -f README.md && ! test -e SECOND.md")))),
                ),
            )

            val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)

            assertEquals(
                PipelineStatus.Success,
                run.status,
                "the pinned commit's tree (first commit) should be checked out, not the branch tip",
            )
        }

    /** Creates a one-commit git repo at [dir] with a README the clone test looks for. */
    private fun seedRepo(dir: Path) {
        Files.writeString(dir.resolve("README.md"), "hello\n")
        git(dir, "init", "-q")
        git(dir, "add", "README.md")
        git(dir, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "-m", "init")
    }

    /** Seeds a two-commit repo and returns the FIRST commit's SHA; allows fetching a reachable commit by id. */
    private fun seedTwoCommitRepo(dir: Path): String {
        seedRepo(dir)
        val firstSha = gitOut(dir, "rev-parse", "HEAD")
        Files.writeString(dir.resolve("SECOND.md"), "second\n")
        git(dir, "add", "SECOND.md")
        git(dir, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "-m", "second")
        git(dir, "config", "uploadpack.allowReachableSHA1InWant", "true")
        return firstSha
    }

    private fun git(dir: Path, vararg args: String) {
        val exit = ProcessBuilder(listOf("git", "-C", dir.toString()) + args)
            .redirectErrorStream(true)
            .start()
            .waitFor()
        check(exit == 0) { "git ${args.joinToString(" ")} failed ($exit)" }
    }

    private fun gitOut(dir: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git", "-C", dir.toString()) + args).start()
        val out = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed" }
        return out
    }

    private fun onPath(binary: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(File.pathSeparator).any { dir -> Files.isExecutable(Path.of(dir, binary)) }
    }
}
