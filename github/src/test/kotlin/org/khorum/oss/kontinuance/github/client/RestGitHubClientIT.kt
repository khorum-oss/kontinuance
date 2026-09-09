package org.khorum.oss.kontinuance.github.client

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.support.FakeGitHubServer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
    fun `resolves a branch head SHA`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/commits/.+", body = """{"sha":"branchsha","commit":{}}""")
            val client = RestGitHubClient(token = "t", baseUrl = server.baseUrl)
            assertEquals("branchsha", client.branchHead(repo, "main"))
            assertEquals(listOf("application/vnd.github+json"), server.requests.single().accept)
        }
    }

    @Test
    fun `percent-encodes a branch name that would otherwise break the URI`() = runBlocking {
        // Git permits characters that are illegal in a URI — `%`, spaces, `|`, `{}` — and the branch now
        // comes straight from an operator-typed form field, so `URI.create` on the raw value throws
        // IllegalArgumentException before any request is sent.
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/commits/.+", body = """{"sha":"branchsha","commit":{}}""")
            val client = RestGitHubClient(token = "t", baseUrl = server.baseUrl)

            assertEquals("branchsha", client.branchHead(repo, "100%done fix"))

            val request = server.requests.single()
            // Path rules, not form rules: a space is %20, never `+` — GitHub reads a `+` literally.
            assertEquals("/repos/khorum-oss/kontinuance/commits/100%25done%20fix", request.rawUri)
            assertEquals("/repos/khorum-oss/kontinuance/commits/100%done fix", request.path)
        }
    }

    @Test
    fun `keeps a slashed branch name addressable as path segments`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/commits/.+", body = """{"sha":"branchsha","commit":{}}""")
            val client = RestGitHubClient(token = "t", baseUrl = server.baseUrl)

            assertEquals("branchsha", client.branchHead(repo, "feature/foo bar"))

            assertEquals("/repos/khorum-oss/kontinuance/commits/feature/foo%20bar", server.requests.single().rawUri)
        }
    }

    @Test
    fun `a branch name cannot inject dot segments into the API path`() = runBlocking {
        // `..` is not a legal git ref component, so encoding it costs nothing — and leaving it raw lets an
        // operator-typed branch walk the authenticated API to another endpoint entirely.
        FakeGitHubServer().use { server ->
            server.on("GET", ".*", body = """{"sha":"branchsha","commit":{}}""")
            val client = RestGitHubClient(token = "t", baseUrl = server.baseUrl)

            client.branchHead(repo, "x/../../../user/repos")

            val request = server.requests.single()
            assertTrue(request.rawUri.startsWith("/repos/khorum-oss/kontinuance/commits/"), request.rawUri)
            assertTrue(!request.rawUri.contains(".."), request.rawUri)
        }
    }

    @Test
    fun `branch head is null for a missing branch`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/commits/.+", status = 404, body = """{"message":"Not Found"}""")
            val client = RestGitHubClient(token = "t", baseUrl = server.baseUrl)
            assertNull(client.branchHead(repo, "nope"))
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

    @Test
    fun `fetches a file's contents at a ref`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/contents/.+", body = "pipeline:\n  name: \"demo\"\n")
            val client = RestGitHubClient(token = "t0k3n", baseUrl = server.baseUrl)

            val text = client.fileAt(repo, "kontinuance.yml", "abc123")

            assertEquals("pipeline:\n  name: \"demo\"\n", text)
            val request = server.requests.single()
            assertEquals("/repos/khorum-oss/kontinuance/contents/kontinuance.yml", request.path)
            assertTrue(request.rawUri.contains("ref=abc123"), request.rawUri)
            assertEquals("Bearer t0k3n", request.authorization)
            assertEquals(listOf("application/vnd.github.raw"), request.accept)
        }
    }

    @Test
    fun `encodes a file's path and ref with path rules, not form rules`() = runBlocking {
        // URLEncoder is *form* encoding: it turns a space into `+`, which GitHub reads as a literal plus.
        // Latent until now only because every caller passes a resolved SHA as the ref.
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/contents/.+", body = "pipeline:\n")
            val client = RestGitHubClient(token = "t", baseUrl = server.baseUrl)

            client.fileAt(repo, "deploy dir/kontinuance.yml", "feature branch")

            val request = server.requests.single()
            assertEquals(
                "/repos/khorum-oss/kontinuance/contents/deploy%20dir/kontinuance.yml",
                request.rawUri.substringBefore('?'),
            )
            assertTrue(request.rawUri.contains("ref=feature%20branch"), request.rawUri)
        }
    }

    @Test
    fun `returns null when the file does not exist at that ref`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/contents/.+", status = 404, body = """{"message":"Not Found"}""")
            val client = RestGitHubClient(token = "t0k3n", baseUrl = server.baseUrl)

            assertNull(client.fileAt(repo, "kontinuance.yml", "abc123"))
        }
    }

    @Test
    fun `raises on a non-404 failure rather than reporting an absent file`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/contents/.+", status = 500, body = """{"message":"boom"}""")
            val client = RestGitHubClient(token = "t0k3n", baseUrl = server.baseUrl)

            val error = assertFailsWith<GitHubApiException> { client.fileAt(repo, "kontinuance.yml", "abc") }
            assertEquals(500, error.statusCode)
        }
    }
}
