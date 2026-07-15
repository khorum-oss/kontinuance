package org.khorum.oss.kontinuance.github.client

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.support.FakeGitHubServer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises [RestGitHubClient] against a real HTTP round-trip to the JDK-`HttpServer`-based
 * [FakeGitHubServer] — the sole external seam, integration-tested with zero real network (Constitution II).
 */
class RestGitHubClientIT {

    private val repo = RepoRef("khorum-oss", "kontinuance")

    @Test
    fun `lists and parses open pull requests`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on(
                "GET", "/repos/.+/pulls",
                body = """
                    [{"number":7,"head":{"sha":"abc","ref":"feature"},"base":{"ref":"main"}},
                     {"number":9,"head":{"sha":"def","ref":"fix"},"base":{"ref":"main"}}]
                """.trimIndent(),
            )
            val client = RestGitHubClient(token = "t0k3n", baseUrl = server.baseUrl)

            val prs = client.listOpenPullRequests(repo)

            assertEquals(listOf(7, 9), prs.map { it.number })
            assertEquals("abc", prs.first().headSha)
            assertEquals("feature", prs.first().headRef)
            assertEquals("main", prs.first().baseRef)
        }
    }

    @Test
    fun `creates a commit status with the right path, body, and auth`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("POST", "/repos/.+/statuses/.+", status = 201, body = "{}")
            val client = RestGitHubClient(token = "t0k3n", baseUrl = server.baseUrl)

            client.createCommitStatus(
                repo, "abc123",
                CommitStatus(CommitStatus.State.SUCCESS, "kontinuance/ci", "All stages passed"),
            )

            val request = server.requests.single { it.method == "POST" }
            assertEquals("/repos/khorum-oss/kontinuance/statuses/abc123", request.path)
            assertEquals("Bearer t0k3n", request.authorization)
            assertTrue(request.body.contains("\"state\":\"success\""), "body carries the state")
            assertTrue(request.body.contains("\"context\":\"kontinuance/ci\""), "body carries the context")
        }
    }

    @Test
    fun `a non-success response raises a GitHubApiException carrying the code`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("POST", "/repos/.+/statuses/.+", status = 500, body = """{"message":"boom"}""")
            val client = RestGitHubClient(token = "t", baseUrl = server.baseUrl)

            val error = assertFailsWith<GitHubApiException> {
                client.createCommitStatus(repo, "sha", CommitStatus(CommitStatus.State.PENDING, "c", "d"))
            }
            assertEquals(500, error.statusCode)
        }
    }
}
