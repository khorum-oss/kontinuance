package org.khorum.oss.kontinuance.server

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.persistence.StageRecord
import org.khorum.oss.kontinuance.persistence.StepRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertTrue

/**
 * Exercises `/api/runs/{id}/pipeline` on the real runtime: a seeded run with a persisted stage/step
 * breakdown maps to real pipeline JSON; an unknown run falls back to the fixture.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PipelineIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    @TestConfiguration
    class SeededStore {
        @Bean
        @Primary
        fun seededRunStore(): RunStore = InMemoryRunStore().apply {
            record(
                RunRecord(
                    id = "run-1",
                    pipeline = "demo",
                    status = "Running",
                    stages = listOf(
                        StageRecord(
                            name = "BUILD",
                            status = "Running",
                            steps = listOf(
                                StepRecord("assemble", "Success", "gradle"),
                                StepRecord("test", "Running", "gradle"),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    private val client: WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    private fun body(path: String): String =
        client.get().uri(path).exchange().expectStatus().isOk
            .expectBody(String::class.java).returnResult().responseBody ?: ""

    @Test
    fun `maps the run's real persisted stages`() {
        val json = body("/api/runs/run-1/pipeline")
        assertTrue(json.contains("\"runId\":\"run-1\""))
        assertTrue(json.contains("\"name\":\"BUILD\""))
        assertTrue(json.contains("\"name\":\"assemble\"") && json.contains("\"tool\":\"gradle\""))
        assertTrue(json.contains("\"status\":\"success\"") && json.contains("\"status\":\"running\""))
        assertTrue(json.contains("\"deps\":[]"))
    }

    @Test
    fun `falls back to the fixture for an unknown run`() {
        val json = body("/api/runs/nope/pipeline")
        assertTrue(json.contains("\"stages\":[") && json.contains("CHECKOUT"))
    }
}
