package org.khorum.oss.kontinuance.server.flow.auth

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.server.suite.IntegrationTest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest

/**
 * Open mode (016 / US2): with **no** credentials configured, the API stays reachable without a session, so
 * the existing loopback/dev usage and the current `@SpringBootTest` suite are unaffected. Exercised over a
 * real HTTP round-trip on the running server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthOpenModeIT(
    @param:Value($$"${local.server.port}") private val port: Int,
) : IntegrationTest(port) {
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
