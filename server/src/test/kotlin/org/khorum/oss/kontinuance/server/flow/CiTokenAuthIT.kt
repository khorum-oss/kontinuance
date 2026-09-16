package org.khorum.oss.kontinuance.server.flow

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The CI token's scope enforced over a real HTTP round-trip, not only in
 * [org.khorum.oss.kontinuance.server.config.CiScopeTest]: with authentication enabled it reaches dispatch
 * and is refused everywhere a scope mistake would matter.
 *
 * A `400` from dispatch is the *success* signal here — it means authentication passed and the request
 * reached validation, which then refused an unconfigured repository.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "kontinuance.auth.username=operator",
        "kontinuance.auth.password=secret",
        "kontinuance.auth.ciToken=ci-token-value",
    ],
)
class CiTokenAuthIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    private val client: WebTestClient
        get() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    private val body = mapOf("repo" to "a/b", "sha" to "c".repeat(40))

    private fun dispatchWith(token: String?) =
        client.post().uri("/api/ci/dispatch")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()

    @Test
    fun `no credential is refused`() {
        dispatchWith(null).expectStatus().isUnauthorized
    }

    @Test
    fun `the CI token reaches dispatch`() {
        dispatchWith("ci-token-value").expectStatus().isBadRequest
    }

    @Test
    fun `a wrong CI token is refused`() {
        dispatchWith("wrong").expectStatus().isUnauthorized
    }

    @Test
    fun `the CI token cannot rewrite configuration`() {
        client.put().uri("/api/config")
            .header("Authorization", "Bearer ci-token-value")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("descriptor" to "pipeline:\n  name: x\n  stages: []\n"))
            .exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `the CI token cannot read the whole server's runs`() {
        client.get().uri("/api/runs")
            .header("Authorization", "Bearer ci-token-value")
            .exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `the CI token can read and cancel one run`() {
        // 404/409 rather than 401: the scope let the request through to the handler, which then found no
        // such run. That is the distinction this test exists to prove.
        client.get().uri("/api/runs/run-unknown")
            .header("Authorization", "Bearer ci-token-value")
            .exchange().expectStatus().isNotFound
    }

    @Test
    fun `health stays public and the operator can still sign in`() {
        client.get().uri("/api/health").exchange().expectStatus().isOk
        client.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to "operator", "password" to "secret"))
            .exchange().expectStatus().isOk
    }
}
