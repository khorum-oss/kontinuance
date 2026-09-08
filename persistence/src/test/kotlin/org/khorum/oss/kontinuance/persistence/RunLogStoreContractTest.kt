package org.khorum.oss.kontinuance.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The behaviour every [RunLogStore] backend owes its callers, run against each one (042). */
abstract class RunLogStoreContractTest {

    /** Opens the backend under test over [dir]. Called more than once per test to model a restart. */
    protected abstract fun open(dir: Path): RunLogStore

    @Test
    fun `reads back the lines of one run in the order they were appended`(@TempDir dir: Path) {
        val store = open(dir)
        listOf("[build] compiling", "[test] 12 passed").forEach { store.append("run-1", it) }

        assertEquals(listOf("[build] compiling", "[test] 12 passed"), store.read("run-1"))
    }

    @Test
    fun `an unknown run reads as empty rather than failing`(@TempDir dir: Path) {
        assertTrue(open(dir).read("never-ran").isEmpty())
    }

    @Test
    fun `runs do not see each other's output`(@TempDir dir: Path) {
        val store = open(dir)
        store.append("run-1", "mine")
        store.append("run-2", "theirs")

        assertEquals(listOf("mine"), store.read("run-1"))
        assertEquals(listOf("theirs"), store.read("run-2"))
    }

    @Test
    fun `appended lines survive a restart`(@TempDir dir: Path) {
        open(dir).append("run-1", "recorded before the restart")

        assertEquals(listOf("recorded before the restart"), open(dir).read("run-1"))
    }

    @Test
    fun `concurrent appends to one run keep every line whole`(@TempDir dir: Path) {
        // Steps of one run execute in parallel (the engine's `concurrency` setting), so two of them can
        // append at the same moment. Every line must survive intact — none lost, none spliced together.
        val store = open(dir)
        val lines = 200
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(lines) { i ->
            pool.submit {
                start.await()
                store.append("run-1", "line-$i")
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "appends did not finish in time")

        val recorded = store.read("run-1")
        assertEquals(lines, recorded.size, "every append should have produced exactly one line")
        assertEquals((0 until lines).map { "line-$it" }.toSet(), recorded.toSet())
    }
}

class FileRunLogStoreContractTest : RunLogStoreContractTest() {
    override fun open(dir: Path): RunLogStore = FileRunLogStore(dir)
}

class SqliteRunLogStoreContractTest : RunLogStoreContractTest() {
    override fun open(dir: Path): RunLogStore = SqliteRunLogStore(dir.resolve("kontinuance.db"))
}
