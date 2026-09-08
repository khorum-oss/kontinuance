package org.khorum.oss.kontinuance.server.store

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.persistence.RunStores
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertTrue

/**
 * The default wiring end to end (041): with no backend named, the server opens an embedded SQLite
 * database under the configured store directory, and what it records there is what `/api/runs` and
 * `/api/runs/{id}/logs` serve.
 *
 * Deliberately does NOT override the store beans — every other IT does, which means none of them would
 * notice if the real backend were wired wrong.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SqliteStoreIT(
    @param:Value("\${local.server.port}") private val port: Int,
    @Autowired private val store: RunStore,
    @Autowired private val logs: RunLogStore,
) {

    companion object {
        @TempDir
        @JvmStatic
        lateinit var storeDir: Path

        @JvmStatic
        @DynamicPropertySource
        fun storeLocation(registry: DynamicPropertyRegistry) {
            registry.add("kontinuance.store") { storeDir.toString() }
        }
    }

    private val client: WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @Test
    fun `the default backend is a sqlite database in the store directory`() {
        assertTrue(
            storeDir.resolve(RunStores.DATABASE_FILE).exists(),
            "expected ${RunStores.DATABASE_FILE} under $storeDir",
        )
    }

    @Test
    fun `a run recorded through the wired store is served by the API`() {
        store.record(
            RunRecord(id = "run-sqlite-1", pipeline = "demo", status = "Success", project = "kontinuance"),
        )

        val json = client.get().uri("/api/runs").exchange().expectStatus().isOk
            .expectBody<String>().returnResult().responseBody.orEmpty()

        assertTrue(json.contains("\"id\":\"run-sqlite-1\""), "was: $json")
        assertTrue(json.contains("\"project\":\"kontinuance\""), "was: $json")
    }

    @Test
    fun `output appended through the wired log store is served by the API`() {
        logs.append("run-sqlite-2", "[build] compiling")

        val json = client.get().uri("/api/runs/run-sqlite-2/logs").exchange().expectStatus().isOk
            .expectBody<String>().returnResult().responseBody.orEmpty()

        assertTrue(json.contains("[build] compiling"), "was: $json")
    }
}
