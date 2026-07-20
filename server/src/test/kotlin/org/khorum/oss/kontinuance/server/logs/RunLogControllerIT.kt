package org.khorum.oss.kontinuance.server.logs

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunLogStore
import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Exercises `GET /api/runs/{id}/logs` on the real Spring Boot runtime over a live HTTP round-trip (018): a
 * seeded log store yields the recorded, step-prefixed lines for a run, and an id with no recorded output
 * returns an empty log (not a 404).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunLogControllerIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    @TestConfiguration
    class SeededLogs {
        @Bean
        @Primary
        fun logs(): RunLogStore = InMemoryRunLogStore().apply {
            append("run-7", "[build] compiling")
            append("run-7", "[test] 12 passed")
        }
    }

    private val client: WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @Test
    fun `returns the recorded lines for a run`() {
        client.get().uri("/api/runs/run-7/logs").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.runId").isEqualTo("run-7")
            .jsonPath("$.lines[0]").isEqualTo("[build] compiling")
            .jsonPath("$.lines[1]").isEqualTo("[test] 12 passed")
    }

    @Test
    fun `an unknown run id returns an empty log, not a 404`() {
        client.get().uri("/api/runs/never-ran/logs").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.lines").isEmpty
    }
}
