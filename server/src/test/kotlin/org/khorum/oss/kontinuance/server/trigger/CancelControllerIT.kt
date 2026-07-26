package org.khorum.oss.kontinuance.server.trigger

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Exercises `POST /api/runs/{id}/cancel` (028) status mapping over a live HTTP round-trip: a running run is
 * accepted (`200`), a terminal run is a conflict (`409`), and an unknown id is `404`. The end-to-end
 * cancel→Cancelled behavior is covered by the engine `CancellationTest`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CancelControllerIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    @TestConfiguration
    class SeededStore {
        @Bean
        @Primary
        fun seededRunStore(): RunStore = InMemoryRunStore().apply {
            record(RunRecord(id = "running-1", pipeline = "p", status = "Running"))
            record(RunRecord(id = "done-1", pipeline = "p", status = "Success"))
        }
    }

    private val client: WebTestClient
        get() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @Test
    fun `cancelling a running run is accepted`() {
        client.post().uri("/api/runs/running-1/cancel").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.status").isEqualTo("cancelling")
    }

    @Test
    fun `cancelling a terminal run is a conflict`() {
        client.post().uri("/api/runs/done-1/cancel").exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.error").exists()
    }

    @Test
    fun `cancelling an unknown run is a not-found`() {
        client.post().uri("/api/runs/nope-404/cancel").exchange()
            .expectStatus().isNotFound
            .expectBody().jsonPath("$.error").exists()
    }
}
