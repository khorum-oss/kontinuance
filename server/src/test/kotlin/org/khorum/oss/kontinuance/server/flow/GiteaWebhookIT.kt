package org.khorum.oss.kontinuance.server.flow

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * `POST /api/hooks/gitea` over the real runtime (043): the signature is the only way in, a delivery maps
 * to the repository's *configured* descriptors, and the cases that must not build — a fork, a branch that
 * is not tracked, a replayed push — do not.
 *
 * Operator credentials are set so the auth filter is enforcing: the route has to be reachable with no
 * session *because of* its signature, not because the server happens to be running open.
 *
 * Gitea itself is a JDK [HttpServer] answering the one API call the receiver makes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiteaWebhookIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    private val client: WebTestClient
        get() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    private fun deliver(event: String, body: String, signature: String? = sign(body)) =
        client.post().uri("/api/hooks/gitea")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Gitea-Event", event)
            .apply { signature?.let { header("X-Gitea-Signature", it) } }
            .bodyValue(body)
            .exchange()

    private fun pullRequest(action: String, sha: String, headRepo: String? = REPO, repo: String = REPO): String {
        val head = if (headRepo == null) """{"sha":"$sha"}""" else """{"sha":"$sha","repo":{"full_name":"$headRepo"}}"""
        return """{"action":"$action","number":7,"pull_request":{"head":$head},"repository":{"full_name":"$repo"}}"""
    }

    private fun push(ref: String, after: String): String =
        """{"ref":"$ref","before":"${"1".repeat(40)}","after":"$after","repository":{"full_name":"$REPO"}}"""

    private fun runIdOf(result: WebTestClient.ResponseSpec): String =
        Regex("\"runId\"\\s*:\\s*\"([^\"]+)\"")
            .find(String(result.expectBody().returnResult().responseBody!!))!!
            .groupValues[1]

    /** Reads the run back with the operator session the filter demands of everything but the hook. */
    private fun run(runId: String): String {
        val cookie = client.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"username":"operator","password":"pw"}""")
            .exchange().expectStatus().isOk
            .returnResult(String::class.java).responseCookies.values.first().first()
        return client.get().uri("/api/runs/$runId").cookie(cookie.name, cookie.value).exchange()
            .expectStatus().isOk
            .expectBody().returnResult().let { String(it.responseBody!!) }
    }

    @Test
    fun `an unsigned delivery is refused with 401 even though the route needs no session`() {
        deliver("push", push("refs/heads/main", HEAD), signature = null).expectStatus().isUnauthorized
    }

    @Test
    fun `a delivery signed with the wrong secret is refused with 401`() {
        val body = push("refs/heads/main", HEAD)
        deliver("push", body, signature = sign(body, "not-the-secret")).expectStatus().isUnauthorized
    }

    @Test
    fun `the rest of the api still demands a session`() {
        client.get().uri("/api/runs").exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `an opened pull request runs the gate at its head commit, recorded as a webhook run`() {
        val sha = "a".repeat(40)
        val id = runIdOf(deliver("pull_request", pullRequest("opened", sha)).expectStatus().isAccepted)

        val run = run(id)
        assert(run.contains("\"relikquary-pr\"")) { "a pull request must gate: $run" }
        assert(run.contains(sha)) { "the run must be pinned to the head commit: $run" }
        assert(run.contains("gitea-webhook")) { "the run must say how it was started: $run" }
    }

    @Test
    fun `a new commit on an open pull request runs the gate again`() {
        deliver("pull_request", pullRequest("synchronized", "b".repeat(40))).expectStatus().isAccepted
    }

    @Test
    fun `a pull request action that changes no code is ignored`() {
        deliver("pull_request", pullRequest("labeled", "c".repeat(40)))
            .expectStatus().isNoContent
            .expectHeader().exists("X-Kontinuance-Ignored")
    }

    @Test
    fun `a pull request from a fork is not built, and neither is one whose head repository is unstated`() {
        deliver("pull_request", pullRequest("opened", "d".repeat(40), headRepo = "stranger/relikquary"))
            .expectStatus().isNoContent
        deliver("pull_request", pullRequest("opened", "d".repeat(40), headRepo = null))
            .expectStatus().isNoContent
    }

    @Test
    fun `a signed delivery for a repository outside the allow-list is refused with a reason`() {
        deliver("pull_request", pullRequest("opened", "e".repeat(40), headRepo = "x/y", repo = "x/y"))
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.error").value<String> { assert(it.contains("x/y")) { it } }
    }

    @Test
    fun `a push to the tracked branch delivers when it is the branch's current head`() {
        val id = runIdOf(deliver("push", push("refs/heads/main", HEAD)).expectStatus().isAccepted)

        assert(run(id).contains("\"relikquary-cd-stage\"")) { "a push must deliver" }
    }

    @Test
    fun `a replayed push for a commit that is no longer the head delivers nothing`() {
        deliver("push", push("refs/heads/main", "9".repeat(40))).expectStatus().isNoContent
    }

    @Test
    fun `a push to another branch, and a branch deletion, are ignored`() {
        deliver("push", push("refs/heads/feature", HEAD)).expectStatus().isNoContent
        deliver("push", push("refs/heads/main", "0".repeat(40))).expectStatus().isNoContent
    }

    @Test
    fun `an event that is neither a push nor a pull request is ignored`() {
        deliver("issues", """{"action":"opened","repository":{"full_name":"$REPO"}}""").expectStatus().isNoContent
    }

    @Test
    fun `a signed body that is not json is refused rather than failing the server`() {
        deliver("push", "not json").expectStatus().isBadRequest
    }

    private companion object {
        const val REPO = "khorum-oss/relikquary"
        const val SECRET = "it-webhook-secret"
        val HEAD = "f".repeat(40)

        fun sign(body: String, secret: String = SECRET): String =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
                .doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Stands in for Gitea: `main` points at [HEAD]. */
        private val gitea: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/repos/$REPO/branches/main") { exchange ->
                val bytes = """{"name":"main","commit":{"id":"$HEAD"}}""".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

        private val dir: Path = Files.createTempDirectory("gitea-webhook-it")
        private val config: Path = dir.resolve("ci.yaml").also {
            listOf("relikquary-pr", "relikquary-cd-stage").forEach { name ->
                Files.writeString(
                    dir.resolve("$name.yaml"),
                    """
                    pipeline:
                      name: $name
                      stages:
                        - name: "noop"
                          steps:
                            - name: "x"
                              run: "true"
                    """.trimIndent(),
                )
            }
            Files.writeString(
                it,
                """
                eventSource:
                  tokenEnv: "GITHUB_TOKEN"
                  cloneBase: "https://gitea.example"
                  repositories:
                    - owner: "khorum-oss"
                      name: "relikquary"
                      prPipeline: "relikquary-pr.yaml"
                      pushPipeline: "relikquary-cd-stage.yaml"
                """.trimIndent(),
            )
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("kontinuance.ci.config") { config.toString() }
            registry.add("kontinuance.store") { dir.resolve("runs").toString() }
            registry.add("kontinuance.auth.username") { "operator" }
            registry.add("kontinuance.auth.password") { "pw" }
            registry.add("kontinuance.hooks.gitea.secret") { SECRET }
            registry.add("kontinuance.hooks.gitea.api-url") { "http://127.0.0.1:${gitea.address.port}" }
            registry.add("kontinuance.github.autostart") { "false" }
        }
    }
}
