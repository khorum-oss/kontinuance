package org.khorum.oss.kontinuance.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertTrue

/**
 * Exercises the migrated API on the **real Spring Boot runtime** — the application context boots on a
 * random port and a live HTTP round-trip via [WebTestClient] asserts the `/api` contract is unchanged
 * from 007 (FR-001 / SC-001 / SC-005, Constitution II), plus actuator health (FR-002). The run store is
 * overridden with a seeded in-memory store so the assertions are deterministic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunApiContractIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    private val client: WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @TestConfiguration
    class SeededStore {
        @Bean
        @Primary
        fun seededRunStore(): RunStore = InMemoryRunStore().apply {
            listOf("a", "b", "c").forEach { record(RunRecord(id = it, pipeline = "p", status = "Success")) }
        }
    }

    private fun body(path: String): String =
        client.get().uri(path).exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).returnResult().responseBody ?: ""

    @Test
    fun `health reports ok`() {
        assertTrue(body("/api/health").contains("\"status\":\"ok\""))
    }

    @Test
    fun `list is newest-first and bounded by limit`() {
        val listBody = body("/api/runs?limit=2")
        assertTrue(listBody.startsWith("{\"runs\":["))
        assertTrue(listBody.contains("\"id\":\"c\"") && listBody.contains("\"id\":\"b\""))
        assertTrue(!listBody.contains("\"id\":\"a\""), "limit=2 excludes the oldest")
    }

    @Test
    fun `detail returns a known run`() {
        assertTrue(body("/api/runs/a").contains("\"id\":\"a\""))
    }

    @Test
    fun `unknown id is 404, unknown route is 404, bad method is 405`() {
        client.get().uri("/api/runs/missing").exchange()
            .expectStatus().isNotFound
            .expectBody(String::class.java).value { assertTrue(it!!.contains("not found")) }
        client.get().uri("/nope").exchange().expectStatus().isNotFound
        client.post().uri("/api/health").exchange().expectStatus().isEqualTo(405)
    }

    @Test
    fun `response content-type is json`() {
        client.get().uri("/api/health").exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
    }

    @Test
    fun `concurrent requests are served without interference`() {
        // Fire many simultaneous requests across endpoints; each must return its own correct response,
        // proving the suspend handlers serve concurrently on the non-blocking stack (US2).
        (1..24).toList().parallelStream().forEach { i ->
            when (i % 3) {
                0 -> assertTrue(body("/api/health").contains("\"status\":\"ok\""))
                1 -> assertTrue(body("/api/runs").contains("\"id\":\"c\""))
                else -> assertTrue(body("/api/runs/a").contains("\"id\":\"a\""))
            }
        }
    }

    @Test
    fun `actuator health reports up`() {
        client.get().uri("/actuator/health").exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).value { assertTrue(it!!.contains("\"status\":\"UP\"")) }
    }
}
