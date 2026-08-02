package org.khorum.oss.kontinuance.server.flow

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.health.FileHeartbeat
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Exercises `GET /api/source` (035) on the real Spring Boot runtime: with an event-source config + cursor
 * file present it reports the watched repositories, cadence, token env-var name (never a value), and the
 * poll cursors; with no config it answers `configured:false`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GitHubSourceControllerIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    private val client: WebTestClient
        get() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @BeforeEach
    fun seed() {
        Files.writeString(configFile, CONFIG_YAML)
        val props = Properties()
        props.setProperty("khorum-oss/relikquary#pr-42", "abc123def")
        props.setProperty("khorum-oss/relikquary#push-main", "0099aabb")
        Files.newOutputStream(cursorFile).use { props.store(it, "kontinuance github poll cursors") }
        Files.deleteIfExists(heartbeatFile) // liveness tests write their own; others expect none
    }

    @Test
    fun `reports the watched repositories, cadence, and token env name (no token value)`() {
        client.get().uri("/api/source")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.configured").isEqualTo(true)
            .jsonPath("$.pollIntervalSeconds").isEqualTo(30)
            .jsonPath("$.baseUrl").isEqualTo("https://api.github.com")
            .jsonPath("$.tokenEnv").isEqualTo("GITHUB_TOKEN")
            .jsonPath("$.token").doesNotExist()
            .jsonPath("$.repositories[0].slug").isEqualTo("khorum-oss/relikquary")
            .jsonPath("$.repositories[0].trackedBranch").isEqualTo("main")
    }

    @Test
    fun `reports the poll cursors with their last-seen commit`() {
        client.get().uri("/api/source")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.cursors[?(@.key == 'khorum-oss/relikquary#pr-42')].sha").isEqualTo("abc123def")
            .jsonPath("$.cursors[?(@.key == 'khorum-oss/relikquary#push-main')].sha").isEqualTo("0099aabb")
    }

    @Test
    fun `a fresh heartbeat is reported live with its cycle count`() {
        FileHeartbeat(heartbeatFile) { System.currentTimeMillis() }.beat()

        client.get().uri("/api/source")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.heartbeat.stale").isEqualTo(false)
            .jsonPath("$.heartbeat.cycles").isEqualTo(1)
            .jsonPath("$.heartbeat.ageSeconds").exists()
    }

    @Test
    fun `a heartbeat older than the stale threshold is reported stale`() {
        // poll interval is 30s → stale beyond 3× (90s); 10_000s ago is well past it.
        FileHeartbeat(heartbeatFile) { System.currentTimeMillis() - 10_000_000L }.beat()

        client.get().uri("/api/source")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.heartbeat.stale").isEqualTo(true)
    }

    @Test
    fun `no heartbeat file yields no heartbeat in the response`() {
        client.get().uri("/api/source")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.configured").isEqualTo(true)
            .jsonPath("$.heartbeat").doesNotExist()
    }

    @Test
    fun `answers configured false when no event-source config is present`() {
        Files.deleteIfExists(configFile)
        client.get().uri("/api/source")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.configured").isEqualTo(false)
    }

    companion object {
        private val configFile: Path = Files.createTempFile("knt-gh-config-", ".yaml")
        private val cursorFile: Path = Files.createTempFile("knt-gh-cursors-", ".properties")
        private val heartbeatFile: Path = Files.createTempFile("knt-gh-heartbeat-", ".properties")

        private val CONFIG_YAML = """
            eventSource:
              tokenEnv: "GITHUB_TOKEN"
              pollIntervalSeconds: 30
              baseUrl: "https://api.github.com"
              repositories:
                - owner: "khorum-oss"
                  name: "relikquary"
                  prPipeline: "pipelines/pr.yaml"
                  pushPipeline: "pipelines/deliver.yaml"
                  trackedBranch: "main"
        """.trimIndent() + "\n"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("kontinuance.github.config") { configFile.toString() }
            registry.add("kontinuance.github.cursors") { cursorFile.toString() }
            registry.add("kontinuance.github.heartbeat") { heartbeatFile.toString() }
        }
    }
}
