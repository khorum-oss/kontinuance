package org.khorum.oss.kontinuance.server.auth

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Open mode (016 / US2): with **no** credentials configured, the API stays reachable without a session, so
 * the existing loopback/dev usage and the current `@SpringBootTest` suite are unaffected. Exercised over a
 * real HTTP round-trip on the running server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthOpenModeIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    @TestConfiguration
    class EmptyStore {
        @Bean
        @Primary
        fun store(): RunStore = InMemoryRunStore()
    }

    private val client: WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @Test
    fun `open mode allows a protected endpoint without a session`() {
        client.get().uri("/api/runs").exchange().expectStatus().isOk
    }

    @Test
    fun `me reports authentication is not required in open mode`() {
        client.get().uri("/api/auth/me").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.authRequired").isEqualTo(false)
    }
}
