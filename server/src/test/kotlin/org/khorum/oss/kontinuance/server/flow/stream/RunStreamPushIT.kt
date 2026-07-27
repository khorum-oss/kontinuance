package org.khorum.oss.kontinuance.server.flow

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.service.RunChangeNotifier
import org.khorum.oss.kontinuance.server.store.NotifyingRunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the 025 push path end-to-end over real HTTP: with `mode=push` and a **60s** poll interval (so the
 * timer cannot deliver within the test window), a run recorded through the notifying store still reaches
 * the SSE stream promptly — it can only have arrived via the in-process change signal. The decorated store
 * seeded here is the real production wiring (`NotifyingRunStore` → `RunChangeNotifier`).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["kontinuance.stream.mode=push", "kontinuance.stream.poll-interval-ms=60000"],
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RunStreamPushIT(
    @param:Value($$"${local.server.port}") private val port: Int,
    private val store: RunStore,
) {

    @TestConfiguration
    class PushStore {
        @Bean
        @Primary
        fun seededRunStore(notifier: RunChangeNotifier): RunStore =
            NotifyingRunStore(
                InMemoryRunStore().apply { record(RunRecord(id = "a", pipeline = "p", status = "Running")) },
                notifier,
            )
    }

    @Test
    fun `a run recorded after subscription is pushed without waiting for the poll`() {
        val client = WebTestClient.bindToServer().baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(15)).build()

        // Record a second run shortly after the stream subscribes; with a 60s poll only the push signal
        // (fired by NotifyingRunStore.record) can surface it inside the collect window.
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler.schedule(
            { store.record(RunRecord(id = "b", pipeline = "p", status = "Running")) },
            500,
            TimeUnit.MILLISECONDS,
        )

        val payloads = client.get().uri("/api/runs/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .returnResult<String>()
            .responseBody
            .take(2)
            .collectList()
            .block(Duration.ofSeconds(12))
        scheduler.shutdownNow()

        assertNotNull(payloads)
        assertTrue(payloads.any { it.contains("\"id\":\"a\"") }, "initial snapshot run missing")
        assertTrue(payloads.any { it.contains("\"id\":\"b\"") }, "pushed run did not arrive via the signal")
    }
}