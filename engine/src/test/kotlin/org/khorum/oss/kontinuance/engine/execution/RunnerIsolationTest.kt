package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The runner-isolation seam wired through the whole engine: a [StepSandbox] injected into
 * [PipelineEngine.default] sees each process step's image and base argv, and whatever it returns is
 * what the engine launches. Uses a recording sandbox that rewrites the command to a benign host
 * command, so the assertion is deterministic without a Docker daemon.
 */
class RunnerIsolationTest {

    /** Records what it was asked to wrap, then returns a harmless host command so the run succeeds. */
    private class RecordingSandbox : StepSandbox {
        val images = CopyOnWriteArrayList<String?>()
        val baseArgvs = CopyOnWriteArrayList<List<String>>()

        override fun wrap(baseArgv: List<String>, context: StepContext): List<String> {
            images.add(context.step.image)
            baseArgvs.add(baseArgv)
            return listOf("/bin/sh", "-c", "true")
        }
    }

    @Test
    fun `a step's image and base command flow through the engine to the sandbox`() = runBlocking {
        val sandbox = RecordingSandbox()
        val pipeline = Pipeline(
            name = "p",
            stages = listOf(
                Stage("s", listOf(Step("build", RunStep("./gradlew assemble"), image = "gradle:8.8-jdk21"))),
            ),
        )

        val run = PipelineEngine.default(CapturingLogSink(), sandbox = sandbox).run(pipeline)

        assertEquals(PipelineStatus.Success, run.status)
        assertEquals("gradle:8.8-jdk21", sandbox.images.single())
        assertEquals(listOf("/bin/sh", "-c", "./gradlew assemble"), sandbox.baseArgvs.single())
    }

    @Test
    fun `an unset image reaches the sandbox as null so host steps are unchanged`() = runBlocking {
        val sandbox = RecordingSandbox()
        val pipeline = Pipeline("p", listOf(Stage("s", listOf(Step("x", RunStep("true"))))))

        PipelineEngine.default(CapturingLogSink(), sandbox = sandbox).run(pipeline)

        assertTrue(sandbox.images.single() == null, "host step should carry no image")
    }
}
