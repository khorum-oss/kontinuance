package org.khorum.oss.kontinuance.server.flow

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.server.domain.project.CreateProjectRequest
import org.khorum.oss.kontinuance.server.domain.project.SourceRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * Exercises the project registry (`/api/projects`, 032) on the real Spring Boot runtime over a live HTTP
 * round-trip: the list seeds a `default` from the current descriptor; a create validates the name and the
 * descriptor (rejecting a bad slug, a duplicate, and unparseable text); and an activate points the live
 * descriptor file at the chosen project.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectControllerIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    private val client: WebTestClient
        get() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @BeforeEach
    fun reset() {
        Files.writeString(descriptorFile, ONE_STAGE)
        if (Files.isDirectory(projectsDir)) {
            Files.list(projectsDir).use { stream -> stream.forEach { Files.deleteIfExists(it) } }
        }
    }

    @Test
    fun `GET seeds a default project from the current descriptor and marks it active`() {
        client.get().uri("/api/projects")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.active").isEqualTo("default")
            .jsonPath("$.projects[0].name").isEqualTo("default")
            .jsonPath("$.projects[0].active").isEqualTo(true)
    }

    @Test
    fun `POST registers a valid project and it then lists`() {
        client.post().uri("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody("billing-api", TWO_STAGES))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("billing-api")

        client.get().uri("/api/projects")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.projects[?(@.name == 'billing-api')]").exists()
    }

    @Test
    fun `POST rejects a duplicate name with 409`() {
        client.post().uri("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody("svc", ONE_STAGE))
            .exchange()
            .expectStatus().isOk

        client.post().uri("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody("svc", ONE_STAGE))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `POST rejects an invalid name with 400`() {
        client.post().uri("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody("../escape", ONE_STAGE))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `POST rejects an unparseable descriptor with 400`() {
        client.post().uri("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody("broken", "pipeline:\n  bogusKey: true\n"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `POST with a malformed body is a 400`() {
        client.post().uri("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("not json")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `activate points the live descriptor at the project and marks it active`() {
        client.post().uri("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody("two", TWO_STAGES))
            .exchange()
            .expectStatus().isOk

        client.post().uri("/api/projects/two/activate")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.active").isEqualTo("two")

        assertEquals(
            TWO_STAGES,
            Files.readString(descriptorFile),
            "activate should write the project's text to the live descriptor"
        )
    }

    @Test
    fun `activate of an unknown project is a 404`() {
        client.post().uri("/api/projects/nope/activate")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `POST with a repo and branch stores the source and GET echoes it`() {
        client.post().uri("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody("sourced", ONE_STAGE, repo = "https://example.test/sourced", branch = "main"))
            .exchange()
            .expectStatus().isOk

        client.get().uri("/api/projects")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.projects[?(@.name == 'sourced')].repo").isEqualTo("https://example.test/sourced")
            .jsonPath("$.projects[?(@.name == 'sourced')].branch").isEqualTo("main")
    }

    @Test
    fun `POST source updates an existing project's source`() {
        client.post().uri("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody("svc", ONE_STAGE))
            .exchange()
            .expectStatus().isOk

        client.post().uri("/api/projects/svc/source")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(SourceRequest("https://example.test/svc", "dev"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.repo").isEqualTo("https://example.test/svc")
            .jsonPath("$.branch").isEqualTo("dev")

        client.get().uri("/api/projects")
            .exchange()
            .expectBody()
            .jsonPath("$.projects[?(@.name == 'svc')].repo").isEqualTo("https://example.test/svc")
    }

    @Test
    fun `POST source for an unknown project is a 404`() {
        client.post().uri("/api/projects/nope/source")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(SourceRequest("https://example.test/x"))
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").exists()
    }

    private fun createBody(name: String, text: String, repo: String? = null, branch: String? = null) =
        CreateProjectRequest(name = name, text = text, repo = repo, branch = branch)

    companion object {
        private val storeDir: Path = Files.createTempDirectory("knt-projects-store-")
        private val projectsDir: Path = storeDir.resolve("projects")
        private val descriptorFile: Path = Files.createTempFile("knt-projects-desc-", ".yml")

        private val ONE_STAGE = """
            pipeline:
              name: "svc"
              stages:
                - name: "build"
                  steps:
                    - name: "assemble"
                      run: "true"
        """.trimIndent() + "\n"

        private val TWO_STAGES = """
            pipeline:
              name: "svc"
              stages:
                - name: "build"
                  steps:
                    - name: "assemble"
                      run: "true"
                - name: "test"
                  steps:
                    - name: "unit"
                      run: "true"
        """.trimIndent() + "\n"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("kontinuance.config.descriptor") { descriptorFile.toString() }
            registry.add("kontinuance.store") { storeDir.toString() }
        }
    }
}
