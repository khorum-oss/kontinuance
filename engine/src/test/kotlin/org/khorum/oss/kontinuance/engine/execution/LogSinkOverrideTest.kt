package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import kotlin.test.assertTrue

/**
 * Verifies the per-invocation log-sink override (018): a sink passed to `run(logSink = …)` receives that
 * run's output, and the engine's configured default sink does not — the seam the server uses to record one
 * run's output into a per-run store.
 */
class LogSinkOverrideTest {

    @Test
    fun `a run-scoped sink receives the run's output instead of the engine default`() = runBlocking {
        val engineDefault = CapturingLogSink()
        val runScoped = CapturingLogSink()
        val engine = DefaultPipelineEngine(StepExecutorRegistry(listOf(RunStepExecutor())), engineDefault)
        val pipeline = Pipeline("p", listOf(Stage("s", listOf(Step("echo", RunStep("echo hello"))))))

        engine.run(pipeline, logSink = runScoped)

        assertTrue(runScoped.lines().any { it == "[echo] hello" }, "override sink got the step-prefixed line")
        assertTrue(engineDefault.lines().isEmpty(), "engine default sink received nothing for this run")
    }
}
