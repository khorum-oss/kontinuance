package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepIsolationTest {

    @Test
    fun `steps share the run workspace via the same relative path`() = runBlocking {
        // 'writer' creates shared.txt; 'checker' passes only if it DOES see that file — which holds
        // because all steps of a run share one workspace directory (a checkout persists across steps).
        val pipeline = Pipeline(
            name = "p",
            stages = listOf(
                Stage(
                    "s",
                    listOf(
                        Step("writer", RunStep("echo hello > shared.txt")),
                        Step("checker", RunStep("test -f shared.txt")),
                    ),
                ),
            ),
        )

        val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)

        assertEquals(PipelineStatus.Success, run.status)
    }

    @Test
    fun `the scoped environment does not leak arbitrary parent variables`() = runBlocking {
        val sink = CapturingLogSink()
        val dir = Files.createTempDirectory("knt-iso-test-")
        try {
            val context = StepContext(
                step = Step("env", RunStep("env")),
                workingDir = dir,
                environment = mapOf("KNT_MARKER" to "present"),
                logSink = sink,
            )

            RunStepExecutor().execute(context)

            val text = sink.text()
            assertTrue(text.contains("KNT_MARKER=present"), "scoped variable missing: $text")
            // HOME exists in the parent process but is not in the scoped environment, so it must not leak.
            if (System.getenv("HOME") != null) {
                assertFalse(text.contains("HOME="), "parent HOME leaked into the step: $text")
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `the run workspace is removed after a terminal status`() = runBlocking {
        val tmp = Path.of(System.getProperty("java.io.tmpdir"))
        fun workspaces(): Set<String> =
            Files.list(tmp).use { stream ->
                stream.map { it.name }.filter { it.startsWith("knt-run-") }.toList().toSet()
            }

        val before = workspaces()
        val pipeline = Pipeline("p", listOf(Stage("s", listOf(Step("x", RunStep("true"))))))

        PipelineEngine.default(CapturingLogSink()).run(pipeline)

        val leftover = workspaces() - before
        assertTrue(leftover.isEmpty(), "leftover run workspaces: $leftover")
    }
}
