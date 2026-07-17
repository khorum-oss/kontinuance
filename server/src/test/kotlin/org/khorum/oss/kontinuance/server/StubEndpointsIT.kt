package org.khorum.oss.kontinuance.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertTrue

/**
 * Exercises the additive STUB endpoints on the real Spring Boot runtime via a live HTTP round-trip. Also
 * asserts the existing `/api/health` contract is unchanged, so adding the stubs did not disturb routing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StubEndpointsIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    private val client: WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    private fun body(path: String): String =
        client.get().uri(path).exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody(String::class.java).returnResult().responseBody ?: ""

    @Test
    fun `pipeline stub returns typed stages for the run`() {
        val json = body("/api/runs/run-7/pipeline")
        assertTrue(json.contains("\"runId\":\"run-7\""))
        assertTrue(json.contains("\"stages\":["))
        assertTrue(json.contains("CHECKOUT") && json.contains("\"tool\":\"gradle\""))
    }

    @Test
    fun `deploy stub returns nodes, artifacts, and environment`() {
        val json = body("/api/deploy")
        assertTrue(json.contains("\"nodes\":[") && json.contains("\"artifacts\":["))
        assertTrue(json.contains("\"environment\":{") && json.contains("\"podsReady\""))
    }

    @Test
    fun `coverage stub is kover-shaped with modules`() {
        val json = body("/api/coverage")
        assertTrue(json.contains("\"tool\":\"kover\""))
        assertTrue(json.contains("\"line\":{") && json.contains("\"branch\":{"))
        assertTrue(json.contains("\"modules\":[") && json.contains("\"name\":\"engine\""))
    }

    @Test
    fun `config stub returns the resolved config and plan`() {
        val json = body("/api/config")
        assertTrue(json.contains("\"source\":\"kontinuance.yml\""))
        assertTrue(json.contains("\"plan\":{") && json.contains("\"maxParallel\":3"))
    }

    @Test
    fun `existing health contract is unchanged`() {
        assertTrue(body("/api/health").contains("\"status\":\"ok\""))
    }
}
