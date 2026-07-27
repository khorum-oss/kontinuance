package org.khorum.oss.kontinuance.server.flow.log

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunLogStore
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunLogStore
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the live log-tail SSE endpoint (`/api/runs/{id}/logs/stream`, 023) on the real Spring Boot
 * runtime over a real HTTP round-trip: a seeded log store yields the recorded lines as `log` events and,
 * because the seeded run is in a terminal state, the stream completes with a terminal `end` event. A fast
 * poll interval keeps the test snappy; the already-recorded lines are delivered on the first poll, so the
 * assertions are deterministic without racing the poll cadence.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["kontinuance.stream.poll-interval-ms=100"],
)
class RunLogStreamIT(
    @param:Value($$"${local.server.port}") private val port: Int,
) {

    @TestConfiguration
    class Seeded {
        @Bean
        @Primary
        fun logs(): RunLogStore = InMemoryRunLogStore().apply {
            append("run-1", "[build] compiling")
            append("run-1", "[test] 12 passed")
        }

        @Bean
        @Primary
        fun runs(): RunStore = InMemoryRunStore().apply {
            // Terminal status → the tail drains its lines and then completes with an `end` event.
            record(RunRecord(id = "run-1", pipeline = "p", status = "Success"))
        }
    }

    private val client: WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(10)).build()

    @Test
    fun `SSE tails the recorded lines and ends when the run is terminal`() {
        val payloads = client.get().uri("/api/runs/run-1/logs/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBody
            .take(2)
            .collectList()
            .block(Duration.ofSeconds(10))

        assertNotNull(payloads)
        assertTrue(payloads.any { it.contains("compiling") }, "tail includes the first recorded line")
        assertTrue(payloads.any { it.contains("12 passed") }, "tail includes the later recorded line")
    }

    @Test
    fun `WebSocket tails the recorded lines as text frames and closes when terminal`() {
        val frames = CopyOnWriteArrayList<String>()
        val client = ReactorNettyWebSocketClient()
        // The seeded run is terminal, so the log flow completes and the server closes the socket, which
        // completes receive() — collecting all frames before the block() returns.
        client.execute(URI("ws://localhost:$port/ws/runs/run-1/logs")) { session ->
            session.receive().map { it.payloadAsText }.doOnNext { frames.add(it) }.then()
        }.block(Duration.ofSeconds(10))

        assertTrue(frames.any { it.contains("compiling") }, "a frame carries the first recorded line")
        assertTrue(frames.any { it.contains("12 passed") }, "a frame carries the later recorded line")
    }
}
