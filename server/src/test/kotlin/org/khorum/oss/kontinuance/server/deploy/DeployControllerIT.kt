package org.khorum.oss.kontinuance.server.deploy

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

/**
 * Exercises `GET /api/deploy` on the real Spring Boot runtime (022): the delivery view is derived from the
 * latest run's real stages — a SOURCE node plus a node per stage with its real status, real
 * stage-completion in the environment panel, an empty artifact manifest, and the honest "external"
 * note (Kontinuance runs no registry and does not query ArgoCD).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeployControllerIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    @TestConfiguration
    class SeededStore {
        @Bean
        @Primary
        fun store(): RunStore = InMemoryRunStore().apply {
            record(
                RunRecord(
                    id = "run-9",
                    pipeline = "kontinuance-service",
                    status = "Failed",
                    repo = "khorum-oss/kontinuance",
                    sha = "a3f19c2ff",
                    trigger = "manual",
                    stages = listOf(
                        StageRecord("build", "Success", listOf(StepRecord("assemble", "Success", "gradle"))),
                        StageRecord("test", "Failed", listOf(StepRecord("unit", "Failed", "gradle"))),
                    ),
                ),
            )
        }
    }

    private val client: WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @Test
    fun `derives the delivery view from the latest run`() {
        client.get().uri("/api/deploy").exchange()
            .expectStatus().isOk
            .expectBody()
            // SOURCE node from the run + a node per real stage (real statuses)
            .jsonPath("$.nodes[0].label").isEqualTo("SOURCE")
            .jsonPath("$.nodes[1].label").isEqualTo("BUILD")
            .jsonPath("$.nodes[1].status").isEqualTo("synced")
            .jsonPath("$.nodes[2].label").isEqualTo("TEST")
            .jsonPath("$.nodes[2].status").isEqualTo("failed")
            // no artifact registry — honest empty manifest
            .jsonPath("$.artifacts").isEmpty
            // real stage-completion + commit; external note (no fabricated pods/sync)
            .jsonPath("$.environment.podsReady").isEqualTo("1/2")
            .jsonPath("$.environment.syncRevision").isEqualTo("a3f19c2")
            .jsonPath("$.environment.health").isEqualTo("FAILED")
    }
}
