package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.execution.StatusEvent
import org.khorum.oss.kontinuance.engine.execution.Target
import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.model.GitStep
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.engine.secret.SecretSource
import org.khorum.oss.kontinuance.github.client.GitHubClient
import org.khorum.oss.kontinuance.github.support.RecordingGitHubClient
import org.khorum.oss.kontinuance.persistence.InMemoryRunLogStore
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.domain.project.DescriptorResolver
import org.khorum.oss.kontinuance.server.domain.project.GitHubClientProvider
import org.khorum.oss.kontinuance.server.domain.project.ProjectSource
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit-tests [RunTrigger] with an in-memory store and a fake engine, using an [kotlinx.coroutines.Dispatchers.Unconfined]
 * scope so the background run executes inline (making the terminal record observable without waiting).
 */
class RunTriggerTest {

    /** A fake engine whose `run` returns [outcome] (or throws [failWith]) without shelling out. */
    private class FakeEngine(
        private val outcome: PipelineStatus = PipelineStatus.Success,
        private val failWith: Throwable? = null,
    ) : PipelineEngine {
        override suspend fun run(
            pipeline: Pipeline,
            secrets: SecretSource,
            completedStages: List<StageRun>,
            logSink: LogSink?,
            runId: RunId?,
        ): Run {
            failWith?.let { throw it }
            logSink?.emit("[demo] hello")
            return Run(runId ?: RunId("engine-generated"), pipeline, outcome, emptyList())
        }

        /** This fake executes nothing step by step, so it reports no transitions. */
        override fun statuses(runId: RunId): Flow<StatusEvent> = emptyFlow()
        override suspend fun cancel(runId: RunId): Unit = throw UnsupportedOperationException()
    }

    /** An engine whose status stream is unusable — progress tracking must not turn that into a failed run. */
    private class NoStatusesEngine : PipelineEngine {
        override suspend fun run(
            pipeline: Pipeline,
            secrets: SecretSource,
            completedStages: List<StageRun>,
            logSink: LogSink?,
            runId: RunId?,
        ): Run = Run(runId ?: RunId("engine-generated"), pipeline, PipelineStatus.Success, emptyList())

        override fun statuses(runId: RunId): Flow<StatusEvent> = throw UnsupportedOperationException()
        override suspend fun cancel(runId: RunId): Unit = throw UnsupportedOperationException()
    }

    /**
     * An engine that reports transitions as a real one does: the step starts, then finishes, before the
     * run returns. Its flow exists before `run` is called, which is what lets a collector subscribe
     * without racing the run.
     */
    private class EmittingEngine(private val stage: String, private val step: String) : PipelineEngine {
        private val events = MutableSharedFlow<StatusEvent>(replay = 16, extraBufferCapacity = 16)

        override suspend fun run(
            pipeline: Pipeline,
            secrets: SecretSource,
            completedStages: List<StageRun>,
            logSink: LogSink?,
            runId: RunId?,
        ): Run {
            val target = Target.StepTarget(pipeline.name, stage, step)
            events.emit(StatusEvent(target, PipelineStatus.Running))
            // A real engine does the step's work here; yielding is what gives the collector its turn,
            // and without it this fake would report the whole run in one indivisible burst.
            yield()
            events.emit(StatusEvent(target, PipelineStatus.Success))
            yield()
            return Run(runId ?: RunId("engine-generated"), pipeline, PipelineStatus.Success, emptyList())
        }

        override fun statuses(runId: RunId): Flow<StatusEvent> = events
        override suspend fun cancel(runId: RunId): Unit = throw UnsupportedOperationException()
    }

    /** Records the pipeline it was handed, so a test can assert what the trigger actually built. */
    private class CapturingEngine : PipelineEngine {
        var pipeline: Pipeline? = null

        override suspend fun run(
            pipeline: Pipeline,
            secrets: SecretSource,
            completedStages: List<StageRun>,
            logSink: LogSink?,
            runId: RunId?,
        ): Run {
            this.pipeline = pipeline
            return Run(runId ?: RunId("engine-generated"), pipeline, PipelineStatus.Success, emptyList())
        }

        override fun statuses(runId: RunId): Flow<StatusEvent> = emptyFlow()
        override suspend fun cancel(runId: RunId): Unit = throw UnsupportedOperationException()
    }

    private val validDescriptor = """
        pipeline:
          name: "demo"
          stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]
    """.trimIndent()

    /**
     * An [InMemoryRunStore] that also keeps every write in order. The store overwrites by id, so without
     * this the initial `Running` record is gone by the time the inline background run has replaced it —
     * and that record is exactly what the runs list shows while a run is in flight.
     */
    private class RecordingRunStore : RunStore {
        private val delegate = InMemoryRunStore()
        val writes = mutableListOf<RunRecord>()

        override fun record(record: RunRecord) {
            writes += record
            delegate.record(record)
        }

        override fun recent(limit: Int): List<RunRecord> = delegate.recent(limit)
        override fun get(id: String): RunRecord? = delegate.get(id)
    }

    private val stagedDescriptor = """
        pipeline:
          name: "demo"
          stages:
            - name: "build"
              steps:
                - name: "assemble"
                  run: "true"
    """.trimIndent()

    private fun triggerFor(
        store: RunStore,
        engine: PipelineEngine,
        path: Path,
        projects: ProjectStore = ProjectStore(path.resolveSibling("projects")),
        client: GitHubClient? = null,
    ): RunTrigger {
        val launcher = RunLauncher(store, engine, CoroutineScope(Dispatchers.Unconfined), InMemoryRunLogStore())
        val resolver = DescriptorResolver(
            projects = projects,
            liveDescriptor = path,
            descriptorPath = "kontinuance.yml",
            clients = GitHubClientProvider { client },
        )
        return RunTrigger(store, launcher, projects, resolver)
    }

    /** Registers [name] as a project and makes it active, as the entry picker does. */
    private fun activate(path: Path, name: String, text: String): ProjectStore =
        ProjectStore(path.resolveSibling("projects")).apply {
            save(name, text)
            setActive(name)
        }

    @Test
    fun `rejects when no descriptor file is present`(@TempDir dir: Path) = runTest {
        val result = triggerFor(InMemoryRunStore(), FakeEngine(), dir.resolve("missing.yml")).trigger()
        assertTrue(result is RunTrigger.Result.Rejected, "expected rejection when descriptor is absent")
    }

    @Test
    fun `rejects an invalid descriptor without recording a run`(@TempDir dir: Path) = runTest {
        val store = InMemoryRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, "pipeline:\n  bogusKey: 1\n")

        val result = triggerFor(store, FakeEngine(), file).trigger()

        assertTrue(result is RunTrigger.Result.Rejected)
        assertTrue(store.recent(10).isEmpty(), "no run should be recorded for an invalid descriptor")
    }

    @Test
    fun `accepts a valid descriptor and records the terminal run under the returned id`(@TempDir dir: Path) = runTest {
        val store = InMemoryRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, validDescriptor)

        val result = triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        assertTrue(result is RunTrigger.Result.Accepted)
        val id = result.id
        assertTrue(id.startsWith("run-"), "id should be a generated run id, was $id")

        // Unconfined scope ran the background block inline, so the terminal record is already stored.
        val record = store.get(id)
        assertNotNull(record, "the run should be recorded under the accepted id")
        assertEquals("demo", record.pipeline)
        assertEquals("Success", record.status)
        assertEquals("manual", record.trigger)
    }

    @Test
    fun `records a Failed run when the engine throws`(@TempDir dir: Path) = runTest {
        val store = InMemoryRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, validDescriptor)

        val result = triggerFor(store, FakeEngine(failWith = IllegalStateException("boom")), file).trigger()

        assertTrue(result is RunTrigger.Result.Accepted)
        val id = result.id
        val record = assertNotNull(store.get(id))
        assertEquals("Failed", record.status)
        assertEquals("boom", record.reason)
    }

    @Test
    fun `records no run when the descriptor cannot be resolved`(@TempDir dir: Path) = runTest {
        val store = InMemoryRunStore()
        // No descriptor file, no active project: resolution must fail before anything is recorded.
        val trigger = triggerFor(store, FakeEngine(), dir.resolve("absent.yml"))

        val result = trigger.trigger()

        assertTrue(result is RunTrigger.Result.Rejected)
        assertTrue(store.recent(10).isEmpty(), "no run should be recorded for an unresolvable descriptor")
    }

    @Test
    fun `pins the checkout to the commit the descriptor was read from`(@TempDir dir: Path) = runTest {
        val store = InMemoryRunStore()
        val engine = CapturingEngine()
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(
            branchHeads = mapOf("main" to "abc1234"),
            files = mapOf("kontinuance.yml" to validDescriptor),
        )

        triggerFor(store, engine, dir.resolve("live.yml"), projects, client).trigger()

        val checkout = engine.pipeline!!.stages.first().steps.first().definition as GitStep
        assertEquals("abc1234", checkout.sha)
        assertNull(checkout.ref)
    }

    @Test
    fun `stamps the active project on every record when the descriptor declares none`(@TempDir dir: Path) = runTest {
        val store = RecordingRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, validDescriptor)
        activate(file, "web-ui", validDescriptor)

        val result = triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        assertTrue(result is RunTrigger.Result.Accepted)
        // Both writes — the immediate `Running` one the runs list shows while the run is in flight, and the
        // terminal one — must name the project, or the run vanishes from the project-scoped list (039).
        assertEquals(2, store.writes.size, "expected a running record and a terminal record")
        assertTrue(store.writes.all { it.project == "web-ui" }, "every record should name the launching project")
    }

    @Test
    fun `the project it was launched under wins over the descriptor's own project`(@TempDir dir: Path) = runTest {
        val store = RecordingRunStore()
        val declaring = validDescriptor.replace("name: \"demo\"", "name: \"demo\"\n  project: \"platform\"")
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, declaring)
        activate(file, "web-ui", declaring)

        triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        // The launching project owns the run. A descriptor lives in a repository the operator may not
        // control, so letting its `project:` key win files the run under a name nobody is looking at —
        // the runs list is scoped to the active project, so the run executes, persists, and is invisible.
        // Losing a run to a silent filter is worse than ignoring a key the descriptor cannot justify.
        assertTrue(store.writes.all { it.project == "web-ui" }, "the launching project takes precedence")
    }

    @Test
    fun `falls back to the descriptor's project when no project is active`(@TempDir dir: Path) = runTest {
        val store = RecordingRunStore()
        val declaring = validDescriptor.replace("name: \"demo\"", "name: \"demo\"\n  project: \"platform\"")
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, declaring)

        triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        // Nothing was launched under, so the descriptor's key is the only owner on offer — it still names
        // the run rather than leaving it to the repo-name fallback.
        assertTrue(store.writes.all { it.project == "platform" }, "the descriptor names the run when nothing is active")
    }

    @Test
    fun `leaves the project unset when nothing is active and the descriptor declares none`(@TempDir dir: Path) = runTest {
        val store = RecordingRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, validDescriptor)

        triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        // No invented owner: the reader still falls back to the repo's short name (039).
        assertTrue(store.writes.all { it.project == null })
    }

    @Test
    fun `finishing a run keeps the commit and start time the trigger recorded`(@TempDir dir: Path) = runTest {
        // Each record replaces the last by id, so a field the engine cannot report is not merely absent
        // from the terminal write — it erases what the `Running` record already had. That is how a run
        // lost its commit the moment it finished.
        val store = RecordingRunStore()
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(
            branchHeads = mapOf("main" to "abc1234"),
            files = mapOf("kontinuance.yml" to validDescriptor),
        )

        triggerFor(store, FakeEngine(PipelineStatus.Success), dir.resolve("live.yml"), projects, client).trigger()

        assertEquals(2, store.writes.size, "expected a running record and a terminal record")
        assertTrue(store.writes.all { it.sha == "abc1234" }, "every record should name the commit it ran")
        assertTrue(store.writes.all { it.repo != null }, "every record should name the repository")
        assertTrue(store.writes.all { it.startedAt != null }, "the terminal record keeps a start time")
    }

    @Test
    fun `a run that fails before any step keeps its commit`(@TempDir dir: Path) = runTest {
        val store = RecordingRunStore()
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(
            branchHeads = mapOf("main" to "abc1234"),
            files = mapOf("kontinuance.yml" to validDescriptor),
        )
        val engine = FakeEngine(failWith = IllegalStateException("boom"))

        triggerFor(store, engine, dir.resolve("live.yml"), projects, client).trigger()

        val terminal = store.writes.last()
        assertEquals("Failed", terminal.status)
        assertEquals("abc1234", terminal.sha, "the failure record keeps the commit too")
    }

    @Test
    fun `a step going Running is persisted while the run is still in flight`(@TempDir dir: Path) = runTest {
        // The symptom this exists for: every step read `Pending` at 0% for the whole build and then
        // jumped to done, so a running pipeline never looked like it was running.
        val store = RecordingRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, stagedDescriptor)

        triggerFor(store, EmittingEngine(stage = "build", step = "assemble"), file).trigger()

        val midRun = store.writes.filter { it.status == "Running" }
        val sawStepRunning = midRun.any { record ->
            record.stages.any { stage -> stage.steps.any { it.name == "assemble" && it.status == "Running" } }
        }
        assertTrue(sawStepRunning, "a running step should reach the store before the run finishes")
        assertEquals("Success", assertNotNull(store.get(store.writes.last().id)).status)
    }

    @Test
    fun `an unusable status stream leaves the run alone`(@TempDir dir: Path) = runTest {
        // Progress reporting is observability. If the engine cannot hand over a stream, the run still
        // runs and still records its result — it just does so untracked.
        val store = RecordingRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, validDescriptor)

        val result = triggerFor(store, NoStatusesEngine(), file).trigger()

        assertTrue(result is RunTrigger.Result.Accepted)
        assertEquals("Success", assertNotNull(store.get(result.id)).status)
    }

    @Test
    fun `records the declared stage breakdown before the run produces any result`(@TempDir dir: Path) = runTest {
        val store = RecordingRunStore()
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, stagedDescriptor)

        triggerFor(store, FakeEngine(PipelineStatus.Success), file).trigger()

        val running = store.writes.first()
        assertEquals("Running", running.status)
        val stage = running.stages.single()
        assertEquals("build", stage.name)
        assertEquals("Pending", stage.status)
        val step = stage.steps.single()
        assertEquals("assemble", step.name)
        assertEquals("Pending", step.status)
        assertNull(step.startedAt, "a step that has not started carries no start time")
    }
}
