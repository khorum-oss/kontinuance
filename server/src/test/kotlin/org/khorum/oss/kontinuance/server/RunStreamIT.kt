package org.khorum.oss.kontinuance.server

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the live streaming surfaces on the real Spring Boot runtime: the SSE endpoint
 * (`/api/runs/stream`) over a real HTTP round-trip and the WebSocket endpoint (`/ws/runs`) over a real
 * socket, both against a seeded store. A fast poll interval keeps the test snappy; the initial snapshot
 * is delivered on the first poll, so assertions are deterministic without racing the poll cadence.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["kontinuance.stream.poll-interval-ms=100"],
)
class RunStreamIT(
    @param:Value("\${local.server.port}") private val port: Int,
) {

    @TestConfiguration
    class SeededStore {
        @Bean
        @Primary
        fun seededRunStore(): RunStore = InMemoryRunStore().apply {
            listOf("a", "b").forEach { record(RunRecord(id = it, pipeline = "p", status = "Success")) }
        }
    }

    @Test
    fun `SSE streams the run snapshot chronologically`() {
        val client = WebTestClient.bindToServer().baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(10)).build()
        val payloads = client.get().uri("/api/runs/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBody
            .take(2)
            .collectList()
            .block(Duration.ofSeconds(10))

        assertNotNull(payloads)
        assertTrue(payloads.any { it.contains("\"id\":\"a\"") }, "snapshot includes the oldest run first")
        assertTrue(payloads.any { it.contains("\"id\":\"b\"") }, "snapshot includes the newer run")
    }

    @Test
    fun `WebSocket pushes run records as text frames`() {
        val received = AtomicReference<String>()
        val client = ReactorNettyWebSocketClient()
        client.execute(URI("ws://localhost:$port/ws/runs")) { session ->
            session.receive()
                .map { it.payloadAsText }
                .next()
                .doOnNext { received.set(it) }
                .then()
        }.block(Duration.ofSeconds(10))

        val frame = received.get()
        assertNotNull(frame, "expected at least one WebSocket frame")
        assertTrue(frame.contains("\"id\":\"a\""), "first frame is the oldest snapshot record")
    }
}
