package org.khorum.oss.kontinuance.server.flow

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.config.EventSourceConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.khorum.oss.kontinuance.server.store.SessionStore
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises connecting a GitHub repository from the dashboard (040) on the real Spring Boot runtime, with
 * operator authentication enabled — the write endpoints require it.
 *
 * The poller itself is not asserted here: starting it would poll api.github.com. What is asserted is
 * everything around it — the config the connect endpoint writes (which the event source's own parser must
 * read back), the token's handling, the reported state, and the refusals.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GitHubConnectIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    private val client: WebTestClient
        get() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @BeforeEach
    fun clean() {
        configFile.deleteIfExists()
        tokenFile.deleteIfExists()
    }

    @AfterEach
    fun stop() {
        // Leave no source running for the next test, whatever this one did.
        signedIn().delete().uri("/api/source?forget=true").exchange()
    }

    @Test
    fun `connecting writes a config the event source parser reads back`() {
        signedIn().post().uri("/api/source")
            .bodyValue(
                mapOf(
                    "owner" to "acme",
                    "name" to "widgets",
                    "prPipeline" to "/etc/kontinuance/pr.yaml",
                    "pushPipeline" to "/etc/kontinuance/deliver.yaml",
                    "trackedBranch" to "release",
                    "pollIntervalSeconds" to 45,
                    "token" to "ghp-not-a-real-token",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.configured").isEqualTo(true)
            .jsonPath("$.repositories[0].slug").isEqualTo("acme/widgets")
            .jsonPath("$.repositories[0].trackedBranch").isEqualTo("release")
            .jsonPath("$.pollIntervalSeconds").isEqualTo(45)
            .jsonPath("$.hasToken").isEqualTo(true)

        // The written file is the event source's own format, not a private one.
        assertTrue(configFile.isRegularFile(), "connect should have written the config")
        val parsed = EventSourceConfig.load(configFile)
        assertEquals("acme", parsed.bindings.single().repo.owner)
        assertEquals("widgets", parsed.bindings.single().repo.name)
        assertEquals("release", parsed.bindings.single().trackedBranch)
        assertEquals(45, parsed.pollIntervalSeconds)
    }

    @Test
    fun `the token is stored owner-only and never returned by a read`() {
        connect(token = "ghp-not-a-real-token")

        assertEquals("ghp-not-a-real-token", tokenFile.readText())
        val permissions = Files.getPosixFilePermissions(tokenFile)
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)

        client.get().uri("/api/source")
            .cookie(SessionStore.COOKIE, session())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.token").doesNotExist()
            .jsonPath("$.hasToken").isEqualTo(true)
            .jsonPath("$.tokenEnv").isEqualTo("GITHUB_TOKEN")
    }

    @Test
    fun `connecting without any token is refused rather than starting a source that cannot authenticate`() {
        // Names an environment variable that cannot be set, so the outcome does not depend on whatever
        // GITHUB_TOKEN the machine running the suite happens to export.
        signedIn().post().uri("/api/source")
            .bodyValue(
                mapOf(
                    "owner" to "acme", "name" to "widgets", "prPipeline" to "pr.yaml",
                    "tokenEnv" to ABSENT_TOKEN_ENV,
                ),
            )
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").value<String> { assertTrue(it.contains("token"), "should name the missing token: $it") }

        assertFalse(configFile.isRegularFile(), "a refused connect must not leave a config behind")
    }

    @Test
    fun `a blank repository field is rejected`() {
        signedIn().post().uri("/api/source")
            .bodyValue(mapOf("owner" to "acme", "name" to "  ", "prPipeline" to "pr.yaml", "token" to "t"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").value<String> { assertTrue(it.contains("name"), "should name the bad field: $it") }
    }

    @Test
    fun `a poll interval below the floor is rejected`() {
        signedIn().post().uri("/api/source")
            .bodyValue(
                mapOf(
                    "owner" to "acme", "name" to "widgets", "prPipeline" to "pr.yaml",
                    "token" to "t", "pollIntervalSeconds" to 1,
                ),
            )
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `disconnecting stops polling but keeps the config, and forget removes it`() {
        connect(token = "ghp-not-a-real-token")

        signedIn().delete().uri("/api/source")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.configured").isEqualTo(true)
            .jsonPath("$.running").isEqualTo(false)
        assertTrue(configFile.isRegularFile(), "a plain disconnect keeps the config")

        signedIn().delete().uri("/api/source?forget=true")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.configured").isEqualTo(false)
        assertFalse(configFile.isRegularFile(), "forget removes the config")
        assertFalse(tokenFile.isRegularFile(), "forget removes the stored token")
    }

    @Test
    fun `the write endpoints require a session`() {
        client.post().uri("/api/source")
            .bodyValue(mapOf("owner" to "acme", "name" to "widgets", "prPipeline" to "pr.yaml", "token" to "t"))
            .exchange()
            .expectStatus().isUnauthorized

        client.delete().uri("/api/source")
            .exchange()
            .expectStatus().isUnauthorized
    }

    // --- helpers --------------------------------------------------------------------------------------

    private fun connect(token: String) {
        signedIn().post().uri("/api/source")
            .bodyValue(mapOf("owner" to "acme", "name" to "widgets", "prPipeline" to "pr.yaml", "token" to token))
            .exchange()
            .expectStatus().isOk
    }

    private fun signedIn(): WebTestClient = client.mutate()
        .defaultCookie(SessionStore.COOKIE, session())
        .build()

    private fun session(): String {
        val cookie = client.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"username":"$USERNAME","password":"$PASSWORD"}""")
            .exchange()
            .expectStatus().isOk
            .returnResult(Void::class.java)
            .responseCookies
            .getFirst(SessionStore.COOKIE)
        return requireNotNull(cookie).value
    }

    companion object {
        private const val USERNAME = "operator"
        private const val PASSWORD = "correct-horse-battery-staple"

        // No process exports this, so a test naming it is asserting "no token available" and not the
        // machine's ambient GITHUB_TOKEN.
        private const val ABSENT_TOKEN_ENV = "KONTINUANCE_TEST_TOKEN_THAT_IS_NEVER_SET"
                private val stateDir: Path = Files.createTempDirectory("knt-gh-connect-")
        private val configFile: Path = stateDir.resolve("github-source.yaml")
        private val tokenFile: Path = stateDir.resolve("github-token")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("kontinuance.github.config") { configFile.toString() }
            registry.add("kontinuance.github.cursors") { stateDir.resolve("cursors.properties").toString() }
            registry.add("kontinuance.github.heartbeat") { stateDir.resolve("heartbeat.properties").toString() }
            registry.add("kontinuance.github.token-file") { tokenFile.toString() }
            registry.add("kontinuance.auth.username") { USERNAME }
            registry.add("kontinuance.auth.password") { PASSWORD }
        }
    }
}
