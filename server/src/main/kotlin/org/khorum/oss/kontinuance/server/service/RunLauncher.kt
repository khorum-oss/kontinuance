package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.engine.execution.ApprovalToken
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.persistence.StageRecord
import org.khorum.oss.kontinuance.server.domain.RecordingLogSink
import org.springframework.stereotype.Component
import java.time.Instant
import org.khorum.oss.kontinuance.server.domain.project.ProjectResolver

/**
 * Runs a pipeline on the engine in the background under a caller-chosen run id and records the result
 * under that id — whether it finishes (terminal) or pauses at a manual-approval gate
 * (`WaitingOnApproval`, with its completed stages persisted). Shared by the initial trigger and the
 * approve-resume path so both take the same execution route.
 *
 * The run id is carried into the engine via [ApprovalToken] so an approval gate can be addressed by it.
 * [completedStages] resumes a paused run: those stages are skipped and reused rather than re-executed.
 * [context] is the run's repository, commit, and project (033/041/039), carried onto every record this
 * writes so finishing a run never erases what starting it recorded.
 *
 * While the pipeline executes, the engine's per-stage/step transitions are collected into the run's
 * persisted breakdown (042), so the pipeline view shows a build progressing instead of every step sitting
 * `Pending` until the whole run lands at once.
 */
@Component
class RunLauncher(
    private val store: RunStore,
    private val engine: PipelineEngine,
    private val scope: CoroutineScope,
    private val logStore: RunLogStore,
) {
    fun launch(
        id: String,
        pipeline: Pipeline,
        startedAt: Instant,
        completedStages: List<StageRun> = emptyList(),
        context: RunContext = RunContext(),
    ) {
        scope.launch {
            val record = runCatching {
                withContext(ApprovalToken(id)) {
                    // Record this run's masked output under its id (018) so the UI can show real logs.
                    // Pass the server's id as the engine run id so a cancel request can address it (028).
                    val run = trackingProgress(id, pipeline, startedAt, context) {
                        engine.run(
                            pipeline,
                            completedStages = completedStages,
                            logSink = RecordingLogSink(id, logStore),
                            runId = RunId(id),
                        )
                    }
                    // The terminal record REPLACES the `Running` one by id, so anything the engine cannot
                    // report has to be restored here or finishing a run erases it. The engine knows
                    // nothing about the repository (033), the commit the checkout was pinned to (041), or
                    // the project the run was launched under beyond the descriptor's own `project:` key
                    // (039) — and `startedAt`, which it derives from step timings, is absent for a run
                    // whose steps never started.
                    val recorded = RunRecord.from(run, Instant.now(), trigger = "manual")
                    recorded.copy(
                        id = id,
                        repo = context.repo,
                        sha = context.sha,
                        // `recorded` re-derives project from the descriptor (RunRecord.from), so this
                        // write must apply the same rule the trigger did or it re-files the run.
                        project = ProjectResolver.forLaunch(context.project, recorded.project),
                        startedAt = recorded.startedAt ?: startedAt,
                    )
                }
            }.getOrElse {
                RunRecord(
                    id = id,
                    pipeline = pipeline.name,
                    status = "Failed",
                    reason = it.message,
                    startedAt = startedAt,
                    endedAt = Instant.now(),
                    repo = context.repo,
                    sha = context.sha,
                    trigger = "manual",
                    project = context.project,
                    stages = RunRecord.skeleton(pipeline),
                )
            }
            store.record(record)
        }
    }

    /**
     * Runs [block], persisting the run's stage/step breakdown as the engine reports each transition.
     *
     * Subscribing happens before [block] starts, which the engine now allows (`statuses` creates the
     * run's flow on first mention) — otherwise whether any progress was seen would come down to which
     * coroutine won the start. The collector is stopped and joined before returning, so the terminal
     * record the caller writes next can never be overtaken by a late progress write.
     */
    // SwallowedException: containing the failure IS the behaviour — see the catch below. Same stance as
    // FileRunStore's corrupt-record isolation: one broken part must not take the whole thing down.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun <T> trackingProgress(
        id: String,
        pipeline: Pipeline,
        startedAt: Instant,
        context: RunContext,
        block: suspend () -> T,
    ): T {
        // Reporting progress is observability, and observability must never be able to fail a build.
        // If the engine cannot hand over a stream, the run proceeds untracked rather than not at all.
        val events = runCatching { engine.statuses(RunId(id)) }.getOrNull() ?: return block()
        return coroutineScope {
            val progress = RunProgress(pipeline)
            val collector = launch {
                try {
                    events.collect { event ->
                        if (progress.apply(event)) {
                            store.record(running(id, pipeline, startedAt, context, progress.snapshot()))
                        }
                    }
                } catch (e: CancellationException) {
                    // Ours, from the cancelAndJoin below once the run is done.
                    throw e
                } catch (e: Exception) {
                    // Deliberately terminal for the collector and nothing else: a lost or unwritable
                    // progress update costs a stale pipeline view until the run settles, never the run.
                    // The breakdown keeps whatever it had reached, and the terminal record still lands.
                }
            }
            try {
                block()
            } finally {
                collector.cancelAndJoin()
            }
        }
    }

    /** The in-flight record: the run as launched, carrying [stages] as they stand right now. */
    private fun running(
        id: String,
        pipeline: Pipeline,
        startedAt: Instant,
        context: RunContext,
        stages: List<StageRecord>,
    ) = RunRecord(
        id = id,
        pipeline = pipeline.name,
        status = "Running",
        startedAt = startedAt,
        repo = context.repo,
        sha = context.sha,
        trigger = "manual",
        project = context.project,
        stages = stages,
    )
}
