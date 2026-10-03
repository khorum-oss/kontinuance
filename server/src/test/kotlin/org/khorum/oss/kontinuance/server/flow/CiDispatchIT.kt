package org.khorum.oss.kontinuance.server.flow

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.server.domain.ci.CiDispatchRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.file.Files
import java.nio.file.Path

/**
 * `POST /api/ci/dispatch` over the real runtime (007-style contract test): an unconfigured repository and a
 * malformed commit id are both refused with a caller-facing reason, and neither reaches the engine; and the
 * `event` kind selects between the repository's *configured* descriptors over the wire, where the JSON
 * spelling and the fallback for an unrecognised kind are decided.
 *
 * The allow-list is pinned to a temp config rather than left to `~/.kontinuance/ci.yaml`, so the test says
 * the same thing on a developer laptop and on the delivery host.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CiDispatchIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    private val client: WebTestClient
        get() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    private fun dispatch(repo: String, sha: String) =
        client.post().uri("/api/ci/dispatch")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(CiDispatchRequest(repo = repo, sha = sha))
            .exchange()

    /** Posts raw JSON, so the test pins the wire spelling of `event` rather than Kotlin's enum constant. */
    private fun dispatchRaw(repo: String, sha: String, event: String) =
        client.post().uri("/api/ci/dispatch")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"repo":"$repo","sha":"$sha","event":"$event"}""")
            .exchange()

    /** The pipeline the run with this id is recorded against — what the dispatch actually chose to run. */
    private fun pipelineOf(runId: String): String =
        client.get().uri("/api/runs/$runId").exchange()
            .expectStatus().isOk
            .expectBody()
            .returnResult()
            .let { String(it.responseBody!!) }
            .let { Regex("\"pipeline\"\\s*:\\s*\"([^\"]+)\"").find(it)!!.groupValues[1] }

    private fun runIdOf(result: org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec): String =
        Regex("\"runId\"\\s*:\\s*\"([^\"]+)\"")
            .find(String(result.expectBody().returnResult().responseBody!!))!!
            .groupValues[1]

    @Test
    fun `an unconfigured repository is refused with 400 and a reason naming it`() {
        dispatch("attacker/evil", "b".repeat(40))
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").value<String> {
                assert(it.contains("not configured")) { "unhelpful refusal: $it" }
                assert(it.contains("attacker/evil")) { "refusal should name the repo: $it" }
            }
    }

    @Test
    fun `a branch name where a commit id belongs is refused`() {
        dispatch("khorum-oss/relikquary", "main")
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").value<String> {
                assert(it.contains("40")) { "refusal should say what a sha must look like: $it" }
            }
    }


    @Test
    fun `a push dispatch runs the repository's delivery descriptor`() {
        val id = runIdOf(dispatchRaw("khorum-oss/relikquary", "c".repeat(40), "push").expectStatus().isAccepted)

        assert(pipelineOf(id) == "relikquary-cd-stage") { "push must deliver, not gate: ${pipelineOf(id)}" }
    }

    @Test
    fun `an unrecognised event kind falls back to the gate rather than failing to decode`() {
        val id = runIdOf(
            dispatchRaw("khorum-oss/relikquary", "d".repeat(40), "deploy-prod").expectStatus().isAccepted,
        )

        assert(pipelineOf(id) == "relikquary-pr") { "an unknown kind must gate, got: ${pipelineOf(id)}" }
    }

    @Test
    fun `a push dispatch for a repo with no delivery descriptor is refused with a reason`() {
        dispatchRaw("khorum-oss/leyline", "e".repeat(40), "push")
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").value<String> {
                assert(it.contains("pushPipeline")) { "say which configuration is missing: $it" }
                assert(it.contains("khorum-oss/leyline")) { "refusal should name the repo: $it" }
            }
    }

    private companion object {
        private val dir: Path = Files.createTempDirectory("ci-dispatch-it")
        private val config: Path = dir.resolve("ci.yaml").also {
            // A real one-stage descriptor, because a dispatch that is *accepted* parses the file. The
            // previous `stages: []` filler only ever reached the refusal paths, where nothing is parsed —
            // it would have failed the moment a test asserted a run actually started.
            listOf("relikquary-pr", "relikquary-cd-stage", "leyline-pr").forEach { name ->
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
                  repositories:
                    - owner: "khorum-oss"
                      name: "relikquary"
                      prPipeline: "relikquary-pr.yaml"
                      pushPipeline: "relikquary-cd-stage.yaml"
                    # No pushPipeline: delivery is opt-in per repository, and a push for this one is refused.
                    - owner: "khorum-oss"
                      name: "leyline"
                      prPipeline: "leyline-pr.yaml"
                """.trimIndent(),
            )
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("kontinuance.ci.config") { config.toString() }
        }
    }
}
