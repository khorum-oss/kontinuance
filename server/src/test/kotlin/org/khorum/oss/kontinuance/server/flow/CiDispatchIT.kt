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
 * malformed commit id are both refused with a caller-facing reason, and neither reaches the engine.
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

    private companion object {
        private val dir: Path = Files.createTempDirectory("ci-dispatch-it")
        private val config: Path = dir.resolve("ci.yaml").also {
            Files.writeString(dir.resolve("relikquary-pr.yaml"), "pipeline:\n  name: relikquary-pr\n  stages: []\n")
            Files.writeString(
                it,
                """
                eventSource:
                  tokenEnv: "GITHUB_TOKEN"
                  repositories:
                    - owner: "khorum-oss"
                      name: "relikquary"
                      prPipeline: "relikquary-pr.yaml"
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
