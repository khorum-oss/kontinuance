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
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
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
