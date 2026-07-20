package org.khorum.oss.kontinuance.server.auth

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Enforced mode (016 / US1 + US3): with credentials configured, the API rejects sessionless calls, health
 * stays public, and the full sign-in → protected-call → who-am-I → sign-out cycle works. Real HTTP
 * round-trips against the running Spring Boot server (Constitution II — the actual boundary, no mocking).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["kontinuance.auth.username=operator", "kontinuance.auth.password=s3cret"],
)
class AuthIT(
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
    fun `a protected endpoint is rejected without a session, but health is public`() {
        client.get().uri("/api/runs").exchange().expectStatus().isUnauthorized
        client.get().uri("/api/health").exchange().expectStatus().isOk
    }

    @Test
    fun `wrong credentials are rejected`() {
        client.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"username":"operator","password":"nope"}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `sign in, reach a protected endpoint, check identity, then sign out`() {
        val token = signIn()

        client.get().uri("/api/runs").cookie(SessionStore.COOKIE, token)
            .exchange().expectStatus().isOk

        client.get().uri("/api/auth/me").cookie(SessionStore.COOKIE, token)
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.username").isEqualTo("operator")

        client.get().uri("/api/auth/me").exchange().expectStatus().isUnauthorized

        client.post().uri("/api/auth/logout").cookie(SessionStore.COOKIE, token)
            .exchange().expectStatus().isOk

        client.get().uri("/api/runs").cookie(SessionStore.COOKIE, token)
            .exchange().expectStatus().isUnauthorized
    }

    private fun signIn(): String {
        val cookie = client.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"username":"operator","password":"s3cret"}""")
            .exchange()
            .expectStatus().isOk
            .expectCookie().exists(SessionStore.COOKIE)
            .returnResult(String::class.java)
            .responseCookies.getFirst(SessionStore.COOKIE)
        return requireNotNull(cookie).value
    }
}
