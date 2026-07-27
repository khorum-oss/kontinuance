package org.khorum.oss.kontinuance.server.flow

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.server.domain.ConfigUpdateRequest
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
 * Exercises `PUT /api/config` (027) on the real Spring Boot runtime over a live HTTP round-trip: a valid
 * edit is validated by the engine parser, persisted to the descriptor file, and echoed back with a
 * refreshed plan; an invalid edit is rejected `400` and never overwrites the good file on disk.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigWriteIT(
    @param:Value($$"${local.server.port}") private val port: Int,
) {

    private val client: WebTestClient
        get() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @BeforeEach
    fun seed() {
        Files.writeString(descriptorFile, ONE_STAGE)
    }

    @Test
    fun `a valid edit is validated, persisted, and echoed with a refreshed plan`() {
        client.put().uri("/api/config")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ConfigUpdateRequest(TWO_STAGES))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.plan.stages").isEqualTo(2)
            .jsonPath("$.text").isEqualTo(TWO_STAGES)

        assertEquals(TWO_STAGES, Files.readString(descriptorFile), "the descriptor file should hold the new text")
    }

    @Test
    fun `an invalid edit is rejected and the file is left unchanged`() {
        client.put().uri("/api/config")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ConfigUpdateRequest("pipeline:\n  bogusKey: true\n"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()

        assertEquals(ONE_STAGE, Files.readString(descriptorFile), "an invalid edit must not overwrite the file")
    }

    @Test
    fun `a malformed request body is a 400`() {
        client.put().uri("/api/config")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("not json at all")
            .exchange()
            .expectStatus().isBadRequest

        assertEquals(ONE_STAGE, Files.readString(descriptorFile))
    }

    companion object {
        private val descriptorFile: Path = Files.createTempFile("knt-config-write-", ".yml")

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
        fun descriptorProperty(registry: DynamicPropertyRegistry) {
            registry.add("kontinuance.config.descriptor") { descriptorFile.toString() }
        }
    }
}