package org.khorum.oss.kontinuance.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The behaviour every [RunStore] backend owes its callers, run against each one (041).
 *
 * Written once and inherited rather than duplicated per backend: the point of the seam is that the
 * server cannot tell which store it was handed, and a contract that only one implementation is held to
 * is not a contract. Backend-specific detail (on-disk layout, corrupt-file isolation) stays in the
 * per-backend suites.
 */
abstract class RunStoreContractTest {

    /** Opens the backend under test over [dir]. Called more than once per test to model a restart. */
    protected abstract fun open(dir: Path): RunStore

    /**
     * Records [record] as the newest write. Ordering is part of the contract, but the file backend reads
     * it off the filesystem's modification time, whose resolution belongs to the disk rather than to the
     * store — so that subclass stamps an explicit, increasing time here. What the tests below assert is
     * the store's ordering, not the host's clock granularity.
     */
    protected open fun write(store: RunStore, dir: Path, record: RunRecord) = store.record(record)

    private fun record(id: String, project: String? = null) = RunRecord(
        id = id,
        pipeline = "demo",
        status = "Success",
        project = project,
    )

    @Test
    fun `records and fetches a run by id, and answers null for an unknown one`(@TempDir dir: Path) {
        val store = open(dir)
        write(store, dir, record("run-1"))

        assertEquals("run-1", store.get("run-1")?.id)
        assertNull(store.get("missing"))
    }

    @Test
    fun `a recorded run survives a restart`(@TempDir dir: Path) {
        val store = open(dir)
        write(store, dir, record("run-durable"))

        assertEquals("run-durable", open(dir).get("run-durable")?.id)
    }

    @Test
    fun `recording the same id again replaces it rather than adding a second entry`(@TempDir dir: Path) {
        val store = open(dir)
        write(store, dir, record("run-1").copy(status = "Running"))
        write(store, dir, record("run-1").copy(status = "Success"))

        assertEquals("Success", store.get("run-1")?.status)
        assertEquals(1, store.recent(10).count { it.id == "run-1" })
    }

    @Test
    fun `recent lists newest write first and honours the limit`(@TempDir dir: Path) {
        val store = open(dir)
        listOf("a", "b", "c").forEach { write(store, dir, record(it)) }

        assertEquals(listOf("c", "b", "a"), store.recent(10).map { it.id })
        assertEquals(listOf("c", "b"), store.recent(2).map { it.id })
    }

    @Test
    fun `re-recording a run moves it to the front of the listing`(@TempDir dir: Path) {
        // What the runs list depends on: a run that started earlier but just finished is the most recent
        // thing that happened, and the project picker reads the first record per project as its latest.
        val store = open(dir)
        listOf("a", "b").forEach { write(store, dir, record(it)) }
        write(store, dir, record("a").copy(status = "Success"))

        assertEquals("a", store.recent(10).first().id)
    }

    @Test
    fun `recent is empty for a fresh store`(@TempDir dir: Path) {
        assertTrue(open(dir).recent(10).isEmpty())
    }

    @Test
    fun `every field round-trips, including the nested stage breakdown`(@TempDir dir: Path) {
        val started = Instant.parse("2026-09-08T00:00:00Z")
        val full = RunRecord(
            id = "run-full",
            pipeline = "delivery",
            status = "Failed",
            failingStep = "unit",
            reason = "2 failed",
            startedAt = started,
            endedAt = started.plusSeconds(90),
            repo = "khorum-oss/kontinuance",
            sha = "a3f19c2ff",
            trigger = "manual",
            project = "kontinuance",
            stages = listOf(
                StageRecord(
                    name = "BUILD",
                    status = "Success",
                    steps = listOf(
                        StepRecord("assemble", "Success", "gradle", started, started.plusSeconds(30)),
                    ),
                ),
                StageRecord(name = "TEST", status = "Failed", steps = listOf(StepRecord("unit", "Failed"))),
            ),
        )
        write(open(dir), dir, full)

        assertEquals(full, open(dir).get("run-full"))
    }

    @Test
    fun `a run with no optional context round-trips with those fields still absent`(@TempDir dir: Path) {
        // The reader distinguishes "no project" from "some project" (039), so a null must not come back
        // as an empty string.
        val bare = RunRecord(id = "run-bare", pipeline = "demo", status = "Running")
        write(open(dir), dir, bare)

        assertEquals(bare, open(dir).get("run-bare"))
    }
}

class FileRunStoreContractTest : RunStoreContractTest() {
    override fun open(dir: Path): RunStore = FileRunStore(dir)

    private var tick = 0L

    /** Stamps each write with a later modification time, which is what this backend sorts on. */
    override fun write(store: RunStore, dir: Path, record: RunRecord) {
        store.record(record)
        tick += 1_000
        Files.setLastModifiedTime(dir.resolve(record.id + ".json"), FileTime.fromMillis(tick))
    }
}

class SqliteRunStoreContractTest : RunStoreContractTest() {
    override fun open(dir: Path): RunStore = SqliteRunStore(dir.resolve("kontinuance.db"))
}
