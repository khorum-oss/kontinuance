package org.khorum.oss.kontinuance.engine.execution.steps

import org.khorum.oss.kontinuance.engine.execution.ApprovalDecision
import org.khorum.oss.kontinuance.engine.execution.ApprovalGate
import org.khorum.oss.kontinuance.engine.execution.ApprovalToken
import org.khorum.oss.kontinuance.engine.execution.AutoApprovingGate
import org.khorum.oss.kontinuance.engine.execution.StepContext
import org.khorum.oss.kontinuance.engine.execution.StepExecutor
import org.khorum.oss.kontinuance.engine.model.ApprovalStep
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import org.khorum.oss.kontinuance.engine.model.StepRun
import java.time.Instant
import kotlin.coroutines.coroutineContext

/**
 * Executes an [ApprovalStep] by suspending on the injected [ApprovalGate] until the run is approved or
 * rejected — it runs no process. An approval ends the step [PipelineStatus.Success] so the run
 * continues; a rejection ends it [PipelineStatus.Cancelled], which the engine propagates to a
 * [PipelineStatus.Cancelled] run (a deliberate stop, not a failure).
 *
 * The run id used to address the gate is read from the [ApprovalToken] on the coroutine context (set
 * by the host around `engine.run(...)`); it is absent for non-interactive hosts, whose default
 * [AutoApprovingGate] ignores it.
 */
class ApprovalStepExecutor(private val gate: ApprovalGate = AutoApprovingGate) : StepExecutor {

    override fun supports(definition: StepDefinition): Boolean = definition is ApprovalStep

    override suspend fun execute(context: StepContext): StepRun {
        val step = context.step
        val definition = step.definition as ApprovalStep
        val startedAt = Instant.now()
        context.logSink.emit(definition.message)

        val runId = coroutineContext[ApprovalToken]?.runId
        val status = when (gate.await(runId, step.name)) {
            ApprovalDecision.APPROVED -> PipelineStatus.Success
            ApprovalDecision.REJECTED -> PipelineStatus.Cancelled
        }
        return StepRun(step.name, status, exitCode = null, startedAt = startedAt, endedAt = Instant.now())
    }
}
