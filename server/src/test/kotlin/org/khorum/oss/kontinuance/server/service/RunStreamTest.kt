package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.StageRecord
import org.khorum.oss.kontinuance.persistence.StepRecord
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * The stream's emission rule. A run is not a one-shot announcement: the runs list follows a run while it
 * executes, and a run's `status` stays `Running` from its first step to its last — so a stream that speaks
 * once per run leaves every live consumer showing the run exactly as it looked the moment it started.
 */
class RunStreamTest {

    private fun stream(store: InMemoryRunStore) =
        RunStream(store, RunChangeNotifier(), pollIntervalMs = POLL_MS, snapshotLimit = LIMIT, modeRaw = "poll")

    private fun run(id: String, vararg steps: Pair<String, String>) = RunRecord(
        id = id,
        pipeline = "p",
        status = "Running",
        stages = listOf(
            StageRecord(
                name = "build",
                status = "Running",
                steps = steps.map { (n, s) -> StepRecord(name = n, status = s) },
            ),
        ),
    )

    @Test
    fun `re-emits a run whose steps have advanced`() = runBlocking {
        val store = InMemoryRunStore()
        store.record(run("a", "checkout" to "Running", "compile" to "Pending"))

        // Advance the record while the collector is running, exactly as the launcher does mid-run:
        // same id, same status, one more step finished.
        val writer = launch {
            delay(WRITE_DELAY_MS.milliseconds)
            store.record(run("a", "checkout" to "Success", "compile" to "Running"))
        }

        val received = withTimeout(TIMEOUT_MS.milliseconds) {
            stream(store).updates().take(2).toList(mutableListOf<RunRecord>())
        }
        writer.join()

        assertEquals(2, received.size, "the advanced record must reach live consumers")
        assertTrue(received.all { it.id == "a" }, "both emissions describe the same run")
        assertEquals("Running", received[0].stages.first().steps.first().status)
        assertEquals("Success", received[1].stages.first().steps.first().status)
    }

    @Test
    fun `speaks once for a run that has not changed`() = runBlocking {
        val store = InMemoryRunStore()
        store.record(run("a", "checkout" to "Running"))
        store.record(RunRecord(id = "b", pipeline = "p", status = "Success"))

        val received = withTimeout(TIMEOUT_MS.milliseconds) {
            stream(store).updates().take(2).toList(mutableListOf<RunRecord>())
        }

        // Two runs, two emissions: an unchanged record must not be re-sent on every poll.
        assertEquals(setOf("a", "b"), received.map { it.id }.toSet())
    }

    private companion object {
        const val POLL_MS = 50L
        const val LIMIT = 50
        const val TIMEOUT_MS = 5000L
        const val WRITE_DELAY_MS = 200L
    }
}
