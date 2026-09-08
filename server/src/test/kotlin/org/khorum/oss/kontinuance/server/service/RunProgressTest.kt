package org.khorum.oss.kontinuance.server.service

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.execution.StatusEvent
import org.khorum.oss.kontinuance.engine.execution.Target
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The live breakdown the pipeline view reads while a build runs (042). */
class RunProgressTest {

    private val pipeline = Pipeline(
        name = "demo",
        stages = listOf(
            Stage("build", listOf(Step("compile", RunStep("true")))),
            Stage("test", listOf(Step("unit", RunStep("true")), Step("lint", RunStep("true")))),
        ),
    )

    private val clock = Instant.parse("2026-09-08T02:00:00Z")

    private fun progress() = RunProgress(pipeline) { clock }

    private fun step(stage: String, step: String, status: PipelineStatus) =
        StatusEvent(Target.StepTarget("demo", stage, step), status)

    private fun stage(name: String, status: PipelineStatus) =
        StatusEvent(Target.StageTarget("demo", name), status)

    @Test
    fun `starts as the pipeline was declared, with nothing run yet`() {
        val snapshot = progress().snapshot()

        assertEquals(listOf("build", "test"), snapshot.map { it.name })
        assertEquals(listOf("compile"), snapshot[0].steps.map { it.name })
        assertTrue(snapshot.all { it.status == "Pending" })
        assertTrue(snapshot.flatMap { it.steps }.all { it.status == "Pending" })
    }

    @Test
    fun `a step going Running is visible before the run finishes`() {
        // The whole point: a build in progress has to look like one.
        val progress = progress()

        assertTrue(progress.apply(step("build", "compile", PipelineStatus.Running)))

        val compile = progress.snapshot().first { it.name == "build" }.steps.single()
        assertEquals("Running", compile.status)
        assertEquals(clock, compile.startedAt)
        assertNull(compile.endedAt, "a step still running has not ended")
    }

    @Test
    fun `a step reaching a terminal status is stamped with an end time`() {
        val progress = progress()
        progress.apply(step("build", "compile", PipelineStatus.Running))

        assertTrue(progress.apply(step("build", "compile", PipelineStatus.Success)))

        val compile = progress.snapshot().first { it.name == "build" }.steps.single()
        assertEquals("Success", compile.status)
        assertEquals(clock, assertNotNull(compile.endedAt))
    }

    @Test
    fun `stage transitions advance the stage without touching its steps`() {
        val progress = progress()

        assertTrue(progress.apply(stage("test", PipelineStatus.Running)))

        val test = progress.snapshot().first { it.name == "test" }
        assertEquals("Running", test.status)
        assertTrue(test.steps.all { it.status == "Pending" }, "no step has started yet")
    }

    @Test
    fun `one step advancing leaves its siblings alone`() {
        val progress = progress()

        progress.apply(step("test", "unit", PipelineStatus.Running))

        val steps = progress.snapshot().first { it.name == "test" }.steps.associateBy { it.name }
        assertEquals("Running", steps.getValue("unit").status)
        assertEquals("Pending", steps.getValue("lint").status)
    }

    @Test
    fun `a repeated event reports no change so nothing is rewritten`() {
        // Every change is a store write and an SSE frame to every client; an identical one is waste.
        val progress = progress()
        progress.apply(step("build", "compile", PipelineStatus.Running))

        assertFalse(progress.apply(step("build", "compile", PipelineStatus.Running)))
    }

    @Test
    fun `the overall pipeline status is not this projection's business`() {
        // The run record's own `status` is owned by the trigger and the terminal record.
        assertFalse(progress().apply(StatusEvent(Target.PipelineTarget("demo"), PipelineStatus.Running)))
    }

    @Test
    fun `an event for a stage or step the pipeline does not declare is ignored`() {
        // The shape a run shows must stay the shape it was launched with — never invented mid-run.
        val progress = progress()

        assertFalse(progress.apply(step("build", "ghost", PipelineStatus.Running)))
        assertFalse(progress.apply(step("ghost", "compile", PipelineStatus.Running)))
        assertFalse(progress.apply(stage("ghost", PipelineStatus.Running)))
        assertEquals(2, progress.snapshot().size)
    }

    @Test
    fun `each step keeps the tool it was declared with`() {
        assertEquals("run", progress().snapshot().flatMap { it.steps }.first().tool)
    }
}
