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
import org.khorum.oss.kontinuance.server.domain.ci.CiEvent
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

    /**
     * Records the pipeline **and the secret source** it was handed, so a test can assert both what the
     * engine would execute and what it could resolve while doing so. Discarding `secrets` here is what
     * hid the dispatch path's missing `KONTINUANCE_SHA` overlay: the engine validates declared secrets
     * before the first step, so a descriptor needing one fails instantly in production while a test with
     * a no-op engine sails through.
     */
    private class CapturingEngine : PipelineEngine {
        var received: Pipeline? = null
        var receivedSecrets: SecretSource? = null

        override suspend fun run(
            pipeline: Pipeline,
            secrets: SecretSource,
            completedStages: List<StageRun>,
            logSink: LogSink?,
            runId: RunId?,
        ): Run {
            received = pipeline
            receivedSecrets = secrets
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

    private val deliveryDescriptor = descriptor.replace("name: relikquary-pr", "name: relikquary-cd-stage")

    /**
     * A dispatcher over a one-repository allow-list. [push] adds a second allow-listed descriptor as that
     * repository's `pushPipeline`, so a test can tell "no delivery is configured" apart from "delivery is
     * configured and was selected".
     */
    private fun fixture(dir: Path, push: Boolean = false): CiDispatcher {
        Files.writeString(dir.resolve("relikquary-pr.yaml"), descriptor)
        Files.writeString(dir.resolve("relikquary-cd-stage.yaml"), deliveryDescriptor)
        Files.writeString(
            dir.resolve("ci.yaml"),
            """
            eventSource:
              tokenEnv: "GITHUB_TOKEN"
              repositories:
                - owner: "khorum-oss"
                  name: "relikquary"
                  prPipeline: "relikquary-pr.yaml"
            ${if (push) "      pushPipeline: \"relikquary-cd-stage.yaml\"" else ""}
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

    private fun request(
        repo: String = "khorum-oss/relikquary",
        sha: String = head,
        pipeline: String? = null,
        event: CiEvent? = null,
    ) = CiDispatchRequest(repo = repo, sha = sha, pipeline = pipeline, event = event)

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

    @Test
    fun `a push dispatch runs the configured pushPipeline, not the gate`(@TempDir dir: Path) = runTest {
        val accepted = assertIs<CiDispatcher.Result.Accepted>(
            fixture(dir, push = true).dispatch(request(event = CiEvent.PUSH)),
        )

        assertEquals("relikquary-cd-stage", engine.received!!.name)
        assertEquals("relikquary-cd-stage", store.get(accepted.id)!!.pipeline)
    }

    @Test
    fun `a dispatch with no event still runs the gate`(@TempDir dir: Path) = runTest {
        fixture(dir, push = true).dispatch(request())

        assertEquals("relikquary-pr", engine.received!!.name, "an absent event must not change the gate")
    }

    @Test
    fun `a push dispatch is refused when the repo has no pushPipeline configured`(@TempDir dir: Path) = runTest {
        val result = fixture(dir).dispatch(request(event = CiEvent.PUSH))

        val reason = assertIs<CiDispatcher.Result.Rejected>(result).reason
        assertTrue(reason.contains("push"), "the refusal must say delivery is unconfigured, got: $reason")
        assertEquals(null, engine.received, "a refused dispatch must not reach the engine")
    }

    @Test
    fun `a push dispatch asserting the gate's descriptor is refused`(@TempDir dir: Path) = runTest {
        val result = fixture(dir, push = true)
            .dispatch(request(pipeline = "relikquary-pr.yaml", event = CiEvent.PUSH))

        val reason = assertIs<CiDispatcher.Result.Rejected>(result).reason
        assertTrue(reason.contains("relikquary-cd-stage.yaml"), "name the descriptor it would run, got: $reason")
    }

    @Test
    fun `delivery and the gate for one commit are separate runs`(@TempDir dir: Path) = runTest {
        val dispatcher = fixture(dir, push = true)
        store.record(
            RunRecord(
                id = "run-gate", pipeline = "relikquary-pr", status = "Running",
                repo = "khorum-oss/relikquary", sha = head, startedAt = Instant.now(),
            ),
        )

        assertIs<CiDispatcher.Result.Accepted>(dispatcher.dispatch(request(event = CiEvent.PUSH)))
    }

    @Test
    fun `the dispatched commit is resolvable as KONTINUANCE_SHA`(@TempDir dir: Path) = runTest {
        fixture(dir).dispatch(request())

        assertEquals(
            head,
            engine.receivedSecrets!!.resolve("KONTINUANCE_SHA"),
            "a descriptor declaring KONTINUANCE_SHA must resolve it to the commit being built",
        )
    }

    @Test
    fun `delivery resolves KONTINUANCE_SHA too, since its image tags are built from it`(
        @TempDir dir: Path,
    ) = runTest {
        fixture(dir, push = true).dispatch(request(event = CiEvent.PUSH))

        assertEquals(head, engine.receivedSecrets!!.resolve("KONTINUANCE_SHA"))
    }

    @Test
    fun `other secrets still fall through to the base source`(@TempDir dir: Path) = runTest {
        fixture(dir).dispatch(request())

        assertEquals(
            null,
            engine.receivedSecrets!!.resolve("SOME_OTHER_SECRET"),
            "the overlay must answer only for the commit, not shadow every lookup",
        )
    }
}
