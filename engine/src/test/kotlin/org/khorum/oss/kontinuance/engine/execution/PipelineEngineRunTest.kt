package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PipelineEngineRunTest {

    private fun step(name: String, command: String) = Step(name, RunStep(command))

    @Test
    fun `two-stage pipeline of passing commands succeeds`() = runBlocking {
        val pipeline = Pipeline(
            name = "p",
            stages = listOf(
                Stage("build", listOf(step("compile", "true"))),
                Stage("test", listOf(step("unit", "true"))),
            ),
        )

        val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)

        assertEquals(PipelineStatus.Success, run.status)
        assertTrue(run.stageRuns.all { it.status == PipelineStatus.Success })
        assertEquals(listOf("compile", "unit"), run.stageRuns.flatMap { it.stepRuns }.map { it.name })
    }

    @Test
    fun `a failing step fails the run naming the step and stops the stage`() = runBlocking {
        val pipeline = Pipeline(
            name = "p",
            stages = listOf(
                Stage(
                    "s",
                    listOf(
                        step("first", "exit 3"),
                        step("second", "true"),
                    ),
                ),
            ),
        )

        val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)

        val status = assertIs<PipelineStatus.Failed>(run.status)
        assertEquals("first", status.step)

        val stage = run.stageRuns.single()
        // The second step must not run after the first fails.
        assertEquals(listOf("first"), stage.stepRuns.map { it.name })
        assertEquals(3, stage.stepRuns.single().exitCode)
    }

    @Test
    fun `steps execute in declared order`() = runBlocking {
        val sink = CapturingLogSink()
        val pipeline = Pipeline(
            name = "p",
            stages = listOf(
                Stage(
                    "s",
                    listOf(
                        step("one", "echo one"),
                        step("two", "echo two"),
                    ),
                ),
            ),
        )

        PipelineEngine.default(sink).run(pipeline)

        val indexOne = sink.lines().indexOfFirst { it.contains("one") }
        val indexTwo = sink.lines().indexOfFirst { it.contains("two") }
        assertTrue(indexOne in 0 until indexTwo, "expected 'one' before 'two' in ${sink.lines()}")
    }

    @Test
    fun `an empty pipeline succeeds`() = runBlocking {
        val run = PipelineEngine.default(CapturingLogSink()).run(Pipeline("empty"))
        assertEquals(PipelineStatus.Success, run.status)
        assertTrue(run.stageRuns.isEmpty())
    }

    @Test
    fun `an empty stage succeeds`() = runBlocking {
        val pipeline = Pipeline("p", listOf(Stage("s")))
        val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)
        assertEquals(PipelineStatus.Success, run.status)
        assertEquals(PipelineStatus.Success, run.stageRuns.single().status)
    }
}
