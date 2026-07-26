package org.khorum.oss.kontinuance.server.stream

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
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the 025 push path for the log tail end-to-end over real HTTP: with `mode=push` and a **60s** poll
 * interval, a line appended through the notifying log store after subscription reaches the SSE tail
 * promptly — only the in-process log-signal (fired by `NotifyingRunLogStore.append`) could have delivered
 * it. The seeded run is left `Running` (non-terminal) so the tail stays open awaiting the pushed line.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["kontinuance.stream.mode=push", "kontinuance.stream.poll-interval-ms=60000"],
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RunLogStreamPushIT(
    @param:Value("\${local.server.port}") private val port: Int,
    private val logs: RunLogStore,
) {

    @TestConfiguration
    class PushStores {
        @Bean
        @Primary
        fun seededLogs(notifier: RunChangeNotifier): RunLogStore =
            NotifyingRunLogStore(InMemoryRunLogStore(), notifier)

        @Bean
        @Primary
        fun seededRuns(): RunStore = InMemoryRunStore().apply {
            record(RunRecord(id = "run-1", pipeline = "p", status = "Running")) // non-terminal: tail stays open
        }
    }

    @Test
    fun `a line appended after subscription is pushed without waiting for the poll`() {
        val client = WebTestClient.bindToServer().baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(15)).build()

        val scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler.schedule(
            { logs.append("run-1", "[build] pushed line") },
            500,
            TimeUnit.MILLISECONDS,
        )

        val payloads = client.get().uri("/api/runs/run-1/logs/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBody
            .take(1)
            .collectList()
            .block(Duration.ofSeconds(12))
        scheduler.shutdownNow()

        assertNotNull(payloads)
        assertTrue(payloads.any { it.contains("pushed line") }, "appended line did not arrive via the signal")
    }
}
