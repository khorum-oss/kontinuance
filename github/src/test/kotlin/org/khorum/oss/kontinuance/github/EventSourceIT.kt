package org.khorum.oss.kontinuance.github

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.client.RestGitHubClient
import org.khorum.oss.kontinuance.github.poll.InMemoryCursorStore
import org.khorum.oss.kontinuance.github.poll.Poller
import org.khorum.oss.kontinuance.github.report.RunReporter
import org.khorum.oss.kontinuance.github.support.FakeGitHubServer
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.khorum.oss.kontinuance.github.trigger.TriggerResolver
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end US1: a fake GitHub serves one open PR; the event source polls it, runs the bound pipeline
 * through the real 001 engine, and posts `pending` then the terminal status on the PR head SHA. No real
 * network (the fake is a loopback JDK HttpServer) and no mocking of the engine.
 */
class EventSourceIT {

    private val repo = RepoRef("khorum-oss", "kontinuance")
    private val headSha = "headsha1"

    private fun descriptor(dir: Path, name: String, command: String): Path {
        val file = dir.resolve(name)
        file.writeText(
            """
            pipeline:
              name: "pr-check"
              stages:
                - name: "verify"
                  steps:
                    - name: "step"
                      run: "$command"
            """.trimIndent(),
        )
        return file
    }

    private fun pullsBody() =
        """[{"number":1,"head":{"sha":"$headSha","ref":"feature"},"base":{"ref":"main"}}]"""

    private fun statusPostsFor(server: FakeGitHubServer) =
        server.requests.filter { it.method == "POST" && it.path.endsWith("/statuses/$headSha") }

    @Test
    fun `a passing PR pipeline reports pending then success on the head SHA`(@TempDir dir: Path) = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/pulls", body = pullsBody())
            server.on("POST", "/repos/.+/statuses/.+", status = 201, body = "{}")
            val source = eventSource(server, descriptor(dir, "pr.yaml", "true"))

            val run = source.pollAndRun().single()

            assertEquals(PipelineStatus.Success, run.status)
            val posts = statusPostsFor(server)
            assertEquals(2, posts.size, "expected a pending then a terminal status")
            assertTrue(posts[0].body.contains("\"state\":\"pending\""))
            assertTrue(posts[1].body.contains("\"state\":\"success\""))
            assertTrue(posts[1].body.contains("\"context\":\"kontinuance/ci\""), "stable check context")
        }
    }

    @Test
    fun `a failing PR pipeline reports failure on the head SHA`(@TempDir dir: Path) = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/pulls", body = pullsBody())
            server.on("POST", "/repos/.+/statuses/.+", status = 201, body = "{}")
            val source = eventSource(server, descriptor(dir, "pr.yaml", "exit 3"))

            val run = source.pollAndRun().single()

            assertTrue(run.status is PipelineStatus.Failed)
            assertTrue(statusPostsFor(server).last().body.contains("\"state\":\"failure\""))
        }
    }

    @Test
    fun `a manual trigger runs the pipeline and reports on the given SHA`(@TempDir dir: Path) = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("POST", "/repos/.+/statuses/.+", status = 201, body = "{}")
            val source = eventSource(server, descriptor(dir, "pr.yaml", "true"))

            val run = source.triggerManual(repo, "manualsha")

            assertEquals(PipelineStatus.Success, run?.status)
            val posts = server.requests.filter { it.method == "POST" && it.path.endsWith("/statuses/manualsha") }
            assertEquals(2, posts.size)
            assertTrue(posts.last().body.contains("\"state\":\"success\""))
        }
    }

    @Test
    fun `a manual trigger for an unconfigured repo does nothing`(@TempDir dir: Path) = runBlocking {
        FakeGitHubServer().use { server ->
            val source = eventSource(server, descriptor(dir, "pr.yaml", "true"))
            assertNull(source.triggerManual(RepoRef("someone", "else"), "sha"))
            assertTrue(server.requests.none { it.method == "POST" }, "no status posted for an unconfigured repo")
        }
    }

    @Test
    fun `a CI run is recorded to the store with its context`(@TempDir dir: Path) = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/pulls", body = pullsBody())
            server.on("POST", "/repos/.+/statuses/.+", status = 201, body = "{}")
            val store = InMemoryRunStore()
            val client = RestGitHubClient(token = "t", baseUrl = server.baseUrl)
            val binding = RepositoryBinding(repo, descriptor(dir, "pr.yaml", "true"))
            val source = EventSource(
                poller = Poller(client, listOf(binding), InMemoryCursorStore()),
                resolver = TriggerResolver(listOf(binding)),
                reporter = RunReporter(client),
                runStore = store,
            )

            source.pollAndRun()

            val record = store.recent(10).single()
            assertEquals(headSha, record.sha)
            assertEquals("khorum-oss/kontinuance", record.repo)
            assertEquals("PULL_REQUEST", record.trigger)
            assertEquals("Success", record.status)
        }
    }

    private fun eventSource(server: FakeGitHubServer, prPipeline: Path): EventSource {
        val client = RestGitHubClient(token = "t", baseUrl = server.baseUrl)
        val binding = RepositoryBinding(repo, prPipeline)
        return EventSource(
            poller = Poller(client, listOf(binding), InMemoryCursorStore()),
            resolver = TriggerResolver(listOf(binding)),
            reporter = RunReporter(client),
        )
    }
}
