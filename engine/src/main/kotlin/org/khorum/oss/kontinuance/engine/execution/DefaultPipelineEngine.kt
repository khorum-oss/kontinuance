package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.supervisorScope
import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.logging.StdoutLogSink
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepRun
import org.khorum.oss.kontinuance.engine.secret.SecretSource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process [PipelineEngine] coordinated with structured concurrency.
 *
 * Stages run in declared order and steps within a stage run in declared order (FR-004); the first
 * step failure stops the remaining steps in that stage and the run reports failure naming the step
 * (FR-005). Step launches pass through a [ConcurrencyGate] so simultaneously RUNNING steps never
 * exceed the pipeline's `concurrency` (FR-013). Lifecycle transitions are published as
 * [StatusEvent]s, observable per run via [statuses] (FR-006). A run can be cancelled via [cancel],
 * which terminates in-flight steps and ends the run [PipelineStatus.Cancelled] (FR-014).
 *
 * @param registry the step-executor registry (the step-type seam, FR-016).
 * @param logSink the downstream sink for streamed step output.
 * @param runIdFactory produces a [RunId] per run; injectable so tests can cancel by a known id.
 */
class DefaultPipelineEngine(
    private val registry: StepExecutorRegistry,
    private val logSink: LogSink = StdoutLogSink(),
    private val runIdFactory: () -> RunId = { RunId(UUID.randomUUID().toString()) },
) : PipelineEngine {

    private val runFlows = ConcurrentHashMap<RunId, MutableSharedFlow<StatusEvent>>()
    private val activeRuns = ConcurrentHashMap<RunId, Deferred<*>>()

    private val eventStream = MutableSharedFlow<StatusEvent>(
        replay = REPLAY,
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** All status events across runs, primarily for observation and testing. */
    val events: SharedFlow<StatusEvent> = eventStream.asSharedFlow()

    override fun statuses(runId: RunId): Flow<StatusEvent> =
        runFlows[runId]?.asSharedFlow() ?: emptyFlow()

    override suspend fun cancel(runId: RunId) {
        activeRuns[runId]?.cancel()
    }

    // SwallowedException: cancellation is the signalling mechanism for cancel(); it is intentionally
    // converted into a terminal Cancelled Run rather than propagated to the caller (FR-014).
    @Suppress("SwallowedException")
    override suspend fun run(pipeline: Pipeline, secrets: SecretSource): Run {
        val runId = runIdFactory()
        val flow = newRunFlow()
        runFlows[runId] = flow

        validate(pipeline, secrets)

        val gate = ConcurrencyGate(pipeline.concurrency)
        val stepRunner = StepRunner(registry, secrets, logSink)
        val collected = CopyOnWriteArrayList<StageRun>()
        val pipelineTarget = Target.PipelineTarget(pipeline.name)

        emit(flow, pipelineTarget, PipelineStatus.Running)
        return supervisorScope {
            val execution = async { executeStages(pipeline, gate, stepRunner, flow, collected) }
            activeRuns[runId] = execution
            try {
                val overall = execution.await()
                emit(flow, pipelineTarget, overall)
                Run(runId, pipeline, overall, collected.toList())
            } catch (cancelled: CancellationException) {
                emit(flow, pipelineTarget, PipelineStatus.Cancelled)
                Run(runId, pipeline, PipelineStatus.Cancelled, collected.toList())
            } finally {
                activeRuns.remove(runId)
            }
        }
    }

    /** Fails fast (FR-003) before any step runs: every step type must be supported and every secret resolvable. */
    private fun validate(pipeline: Pipeline, secrets: SecretSource) {
        val steps = pipeline.stages.flatMap { it.steps }
        steps.forEach { registry.executorFor(it.definition) }
        steps.flatMap { it.secrets }
            .map { it.name }
            .distinct()
            .forEach { name -> secrets.resolve(name) ?: throw UnresolvedSecretException(name) }
    }

    private suspend fun executeStages(
        pipeline: Pipeline,
        gate: ConcurrencyGate,
        stepRunner: StepRunner,
        flow: MutableSharedFlow<StatusEvent>,
        collected: MutableList<StageRun>,
    ): PipelineStatus {
        for (stage in pipeline.stages) {
            val stageRun = executeStage(pipeline.name, stage, gate, stepRunner, flow)
            collected.add(stageRun)
            if (stageRun.status.isFailure) {
                return PipelineStatus.Failed(failingStep(stageRun), "stage '${stage.name}' failed")
            }
        }
        return PipelineStatus.Success
    }

    private suspend fun executeStage(
        pipelineName: String,
        stage: Stage,
        gate: ConcurrencyGate,
        stepRunner: StepRunner,
        flow: MutableSharedFlow<StatusEvent>,
    ): StageRun {
        val stageTarget = Target.StageTarget(pipelineName, stage.name)
        emit(flow, stageTarget, PipelineStatus.Running)
        val stepRuns = mutableListOf<StepRun>()
        var stageStatus: PipelineStatus = PipelineStatus.Success
        for (step in stage.steps) {
            val target = Target.StepTarget(pipelineName, stage.name, step.name)
            val stepRun = runStep(step, target, gate, stepRunner, flow)
            stepRuns.add(stepRun)
            if (stepRun.status.isFailure) {
                stageStatus = stepRun.status
                break
            }
        }
        emit(flow, stageTarget, stageStatus)
        return StageRun(stage.name, stageStatus, stepRuns)
    }

    /** Runs a single step (or marks it Skipped), emitting its transitions. */
    private suspend fun runStep(
        step: Step,
        target: Target,
        gate: ConcurrencyGate,
        stepRunner: StepRunner,
        flow: MutableSharedFlow<StatusEvent>,
    ): StepRun {
        if (!step.condition) {
            emit(flow, target, PipelineStatus.Skipped)
            return StepRun(step.name, PipelineStatus.Skipped)
        }
        emit(flow, target, PipelineStatus.Running)
        val stepRun = gate.withPermit { stepRunner.run(step) }
        emit(flow, target, stepRun.status)
        return stepRun
    }

    private fun failingStep(stageRun: StageRun): String? =
        stageRun.stepRuns.firstOrNull { it.status.isFailure }?.name

    private fun emit(flow: MutableSharedFlow<StatusEvent>, target: Target, status: PipelineStatus) {
        val event = StatusEvent(target, status)
        flow.tryEmit(event)
        eventStream.tryEmit(event)
    }

    private fun newRunFlow(): MutableSharedFlow<StatusEvent> =
        MutableSharedFlow(
            replay = REPLAY,
            extraBufferCapacity = BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private companion object {
        const val REPLAY = 128
        const val BUFFER = 256
    }
}
