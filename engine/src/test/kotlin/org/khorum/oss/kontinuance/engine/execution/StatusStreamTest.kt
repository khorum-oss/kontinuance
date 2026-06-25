package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.SecretRef
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.secret.SecretSource
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusStreamTest {

    private fun engine() =
        DefaultPipelineEngine(StepExecutorRegistry(listOf(RunStepExecutor())), CapturingLogSink())

    @Test
    fun `transitions are emitted in lifecycle order`() = runBlocking {
        val engine = engine()
        val pipeline = Pipeline("p", listOf(Stage("s", listOf(Step("x", RunStep("true"))))))

        engine.run(pipeline)

        val events = engine.events.replayCache

        // The step transitions Running -> Success, in that order.
        val stepStatuses = events
            .filter { (it.target as? Target.StepTarget)?.step == "x" }
            .map { it.status }
        assertEquals(listOf(PipelineStatus.Running, PipelineStatus.Success), stepStatuses)

        // The pipeline reports Running before its terminal Success.
        val pipelineStatuses = events
            .filter { it.target is Target.PipelineTarget }
            .map { it.status }
        assertEquals(listOf(PipelineStatus.Running, PipelineStatus.Success), pipelineStatuses)
    }

    @Test
    fun `a secret echoed by a step is masked in streamed logs`() = runBlocking {
        val sink = CapturingLogSink()
        val engine = PipelineEngine.default(sink)
        val secrets = SecretSource { name -> if (name == "TOKEN") "sup3rs3cr3t" else null }

        val pipeline = Pipeline(
            name = "p",
            stages = listOf(
                Stage(
                    "s",
                    listOf(Step("leak", RunStep("echo \$TOKEN"), secrets = listOf(SecretRef("TOKEN")))),
                ),
            ),
        )

        engine.run(pipeline, secrets)

        val text = sink.text()
        assertFalse(text.contains("sup3rs3cr3t"), "secret leaked into logs: $text")
        assertTrue(text.contains("***"), "masked placeholder missing: $text")
    }
}
