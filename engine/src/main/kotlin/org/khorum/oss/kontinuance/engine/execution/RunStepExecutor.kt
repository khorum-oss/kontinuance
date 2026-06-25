package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import org.khorum.oss.kontinuance.engine.model.StepRun
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * The v0 [StepExecutor]: runs a [RunStep] shell command via `ProcessBuilder`.
 *
 * Behavior:
 * - launches `/bin/sh -c <command>` inside the isolated working directory with the scoped
 *   environment supplied on the [StepContext] (the inherited process environment is cleared);
 * - merges stderr into stdout and streams each line through the context's masking sink (FR-010/011);
 * - enforces the step timeout, terminating the whole process tree and reporting
 *   [PipelineStatus.TimedOut] within ~1s of the deadline (FR-009, SC-005);
 * - on cancellation, terminates the process tree before propagating (FR-014).
 */
class RunStepExecutor(
    private val defaultTimeout: Duration = DEFAULT_TIMEOUT,
) : StepExecutor {

    override fun supports(definition: StepDefinition): Boolean = definition is RunStep

    // SwallowedException: a launch failure is intentionally converted into a Failed step status that
    // carries the cause's message, rather than propagated — the run continues to a terminal Run.
    @Suppress("SwallowedException")
    override suspend fun execute(context: StepContext): StepRun = withContext(Dispatchers.IO) {
        val step = context.step
        val command = (step.definition as RunStep).command
        val startedAt = Instant.now()

        val process = try {
            buildProcess(context, command).start()
        } catch (e: IOException) {
            return@withContext failedToLaunch(step, e, startedAt)
        }

        val timeout = step.timeout ?: defaultTimeout
        // Reader is a sibling (not a child of the timeout block) so the timeout can fire while the
        // reader is still blocked on the process stream; we then kill the process — which closes the
        // stream and unblocks the reader — before joining it.
        val reader = launch { streamOutput(process, step.name, context.logSink) }
        try {
            val exit = withTimeout(timeout) { runInterruptible { process.waitFor() } }
            reader.join()
            stepResult(step, exit, startedAt)
        } catch (timedOut: TimeoutCancellationException) {
            terminateAndJoin(process, reader)
            StepRun(step.name, PipelineStatus.TimedOut, startedAt = startedAt, endedAt = Instant.now())
        } catch (cancelled: CancellationException) {
            terminateAndJoin(process, reader)
            throw cancelled
        }
    }

    /** Kills the process tree and joins the reader under [NonCancellable] so cleanup runs even when cancelled. */
    private suspend fun terminateAndJoin(process: Process, reader: Job) {
        withContext(NonCancellable) {
            terminateTree(process)
            reader.join()
        }
    }

    private fun buildProcess(context: StepContext, command: String): ProcessBuilder {
        val builder = ProcessBuilder("/bin/sh", "-c", command)
        builder.directory(context.workingDir.toFile())
        builder.redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(context.environment)
        return builder
    }

    private fun streamOutput(process: Process, stepName: String, sink: LogSink) {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { sink.emit("[$stepName] $it") }
        }
    }

    private fun stepResult(step: Step, exit: Int, startedAt: Instant): StepRun {
        val status = if (exit == 0) {
            PipelineStatus.Success
        } else {
            PipelineStatus.Failed(step.name, "command exited with code $exit")
        }
        return StepRun(step.name, status, exitCode = exit, startedAt = startedAt, endedAt = Instant.now())
    }

    private fun failedToLaunch(step: Step, cause: IOException, startedAt: Instant): StepRun =
        StepRun(
            step.name,
            PipelineStatus.Failed(step.name, "failed to launch command: ${cause.message}"),
            startedAt = startedAt,
            endedAt = Instant.now(),
        )

    private fun terminateTree(process: Process) {
        // Capture descendants before destroying the parent, which could otherwise re-parent them.
        val descendants = process.descendants().toList()
        process.destroyForcibly()
        descendants.forEach { it.destroyForcibly() }
        process.waitFor(CLEANUP_GRACE.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    private companion object {
        val DEFAULT_TIMEOUT: Duration = 1.hours
        val CLEANUP_GRACE: Duration = 5.seconds
    }
}
