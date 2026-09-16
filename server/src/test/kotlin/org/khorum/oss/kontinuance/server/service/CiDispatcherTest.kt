package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.execution.StatusEvent
import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.model.GitStep
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.engine.secret.SecretSource
import org.khorum.oss.kontinuance.persistence.InMemoryRunLogStore
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.server.domain.ci.CiBindings
import org.khorum.oss.kontinuance.server.domain.ci.CiDispatchRequest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit-tests [CiDispatcher] against an in-memory store and a fake engine, on an
 * [Dispatchers.Unconfined] scope so the background run executes inline.
 */
class CiDispatcherTest {

    private val head = "a".repeat(40)
    private val store = InMemoryRunStore()

    /** Records the pipeline it was handed, so a test can assert what the engine would actually execute. */
    private class CapturingEngine : PipelineEngine {
        var received: Pipeline? = null

        override suspend fun run(
            pipeline: Pipeline,
            secrets: SecretSource,
            completedStages: List<StageRun>,
            logSink: LogSink?,
            runId: RunId?,
        ): Run {
            received = pipeline
            return Run(runId ?: RunId("engine-generated"), pipeline, PipelineStatus.Success, emptyList())
        }

        override fun statuses(runId: RunId): Flow<StatusEvent> = emptyFlow()
        override suspend fun cancel(runId: RunId): Unit = throw UnsupportedOperationException()
    }

    private val engine = CapturingEngine()

    private val descriptor = """
        pipeline:
          name: relikquary-pr
          stages:
            - name: checkout
              steps:
                - name: clone-pr-head
                  git:
                    url: "https://github.com/khorum-oss/relikquary.git"
                    ref: "main"
    """.trimIndent()

    private fun fixture(dir: Path): CiDispatcher {
        Files.writeString(dir.resolve("relikquary-pr.yaml"), descriptor)
        Files.writeString(
            dir.resolve("ci.yaml"),
            """
            eventSource:
              tokenEnv: "GITHUB_TOKEN"
              repositories:
                - owner: "khorum-oss"
                  name: "relikquary"
                  prPipeline: "relikquary-pr.yaml"
            """.trimIndent(),
        )
        val launcher = RunLauncher(
            store = store,
            engine = engine,
            scope = CoroutineScope(Dispatchers.Unconfined),
            logStore = InMemoryRunLogStore(),
        )
        return CiDispatcher(store, launcher, CiBindings(dir.resolve("ci.yaml")))
    }

    private fun request(repo: String = "khorum-oss/relikquary", sha: String = head, pipeline: String? = null) =
        CiDispatchRequest(repo = repo, sha = sha, pipeline = pipeline)

    @Test
    fun `accepts a configured repo and records the run against its commit`(@TempDir dir: Path) = runTest {
        val accepted = assertIs<CiDispatcher.Result.Accepted>(fixture(dir).dispatch(request()))

        val record = store.get(accepted.id)!!
        assertEquals(head, record.sha)
        assertEquals("khorum-oss/relikquary", record.repo)
        assertEquals("github-actions", record.trigger)
    }

    @Test
    fun `pins the checkout to the dispatched commit`(@TempDir dir: Path) = runTest {
        fixture(dir).dispatch(request())

        val git = engine.received!!.stages.first().steps.first().definition as GitStep
        assertEquals(head, git.sha, "the engine must check out the commit GitHub named")
        assertEquals(null, git.ref, "a pinned sha clears the descriptor's branch ref")
    }

    @Test
    fun `refuses a repository that is not configured`(@TempDir dir: Path) = runTest {
        val result = fixture(dir).dispatch(request(repo = "attacker/evil"))
        assertTrue(assertIs<CiDispatcher.Result.Rejected>(result).reason.contains("not configured"))
    }

    @Test
    fun `refuses a sha that is not a full commit id`(@TempDir dir: Path) = runTest {
        val result = fixture(dir).dispatch(request(sha = "main"))
        assertTrue(assertIs<CiDispatcher.Result.Rejected>(result).reason.contains("40"))
    }

    @Test
    fun `refuses a pipeline that is not the one configured for the repo`(@TempDir dir: Path) = runTest {
        val result = fixture(dir).dispatch(request(pipeline = "deploy-prod.yaml"))
        assertTrue(assertIs<CiDispatcher.Result.Rejected>(result).reason.contains("deploy-prod.yaml"))
    }

    @Test
    fun `a second dispatch of a running commit attaches to the first run`(@TempDir dir: Path) = runTest {
        val dispatcher = fixture(dir)
        store.record(
            RunRecord(
                id = "run-first", pipeline = "relikquary-pr", status = "Running",
                repo = "khorum-oss/relikquary", sha = head, startedAt = Instant.now(),
            ),
        )
        assertEquals("run-first", assertIs<CiDispatcher.Result.Existing>(dispatcher.dispatch(request())).id)
    }

    @Test
    fun `a finished run of the same commit does not block a fresh dispatch`(@TempDir dir: Path) = runTest {
        val dispatcher = fixture(dir)
        store.record(
            RunRecord(
                id = "run-old", pipeline = "relikquary-pr", status = "Failed",
                repo = "khorum-oss/relikquary", sha = head, startedAt = Instant.now(),
            ),
        )
        assertIs<CiDispatcher.Result.Accepted>(dispatcher.dispatch(request()))
    }
}
