package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds

class CancellationTest {

    /**
     * 1. create a run id
     * 2. create the engine
     *    a. create a registry with a single executor
     *    b. create a log sink
     *    c. default run factory returns the run id
     * 3. create a pipeline with a single step
     * 4. run pipeline asynchronously
     * 5. cancel the run
     */
    @Test
    fun `cancelling an in-flight run ends it Cancelled and terminates the step`() = runBlocking {
        val runId = RunId("fixed-run")
        val engine = DefaultPipelineEngine(
            registry = StepExecutorRegistry(listOf(RunStepExecutor())),
            logSink = CapturingLogSink(),
            runIdFactory = { runId },
        )
        val pipeline = Pipeline(name = "p", stages = listOf(Stage("s", listOf(Step("sleeper", RunStep("sleep 30"))))))

        val execution = async(Dispatchers.Default) { engine.run(pipeline) }
        delay(START_GRACE_MS.milliseconds)
        engine.cancel(runId)
        val run = execution.await()

        assertEquals(PipelineStatus.Cancelled, run.status)

        delay(STABILISE_MS.milliseconds)
        val orphan = ProcessHandle.allProcesses().anyMatch { handle ->
            handle.info().commandLine().map { it.contains("sleep 30") }.orElse(false) ?: false
        }
        assertFalse(orphan, "an orphaned 'sleep 30' process survived cancellation")
    }

    /**
     * 1. create a run id
     * 2. create the engine
     *    a. create a registry with a single executor
     *    b. create a log sink
     *    c. default run factory returns the run id
     * 3. create a pipeline with a single step
     * 4. run pipeline asynchronously
     * 5. cancel the run
     */
    @Test
    fun `cancelling an in-flight run ends it Cancelled and terminates the step - dsl`() = runBlocking {
        val runId = RunId("fixed-run")
        val engine = DefaultPipelineEngine(
            registry = StepExecutorRegistry(listOf(RunStepExecutor())),
            logSink = CapturingLogSink(),
            runIdFactory = { runId },
        )
        val pipeline = pipeline {
            name = "p"
            stages {
                stage {
                    name = "s"
                    steps {
                        step {
                            name = "sleeper"
                            definition = RunStep("sleep 30")
                        }
                    }
                }
            }
        }

        val execution = async(Dispatchers.Default) { engine.run(pipeline) }
        delay(START_GRACE_MS.milliseconds)
        engine.cancel(runId)
        val run = execution.await()

        assertEquals(PipelineStatus.Cancelled, run.status)

        delay(STABILISE_MS.milliseconds)
        val orphan = ProcessHandle.allProcesses().anyMatch { handle ->
            handle.info().commandLine().map { it.contains("sleep 30") }.orElse(false) ?: false
        }
        assertFalse(orphan, "an orphaned 'sleep 30' process survived cancellation")
    }

    private companion object {
        const val START_GRACE_MS = 500L
        const val STABILISE_MS = 300L
    }
}
