package org.khorum.oss.kontinuance.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Backend selection and the one-time import that carries an existing file history into the database (042). */
class RunStoresTest {

    private fun record(id: String) = RunRecord(id = id, pipeline = "demo", status = "Success")

    @Test
    fun `defaults to sqlite when nothing is configured`() {
        assertEquals(RunStores.Backend.SQLITE, RunStores.Backend.of(null))
        assertEquals(RunStores.Backend.SQLITE, RunStores.Backend.of(""))
        assertEquals(RunStores.Backend.SQLITE, RunStores.Backend.of("   "))
    }

    @Test
    fun `names are matched case-insensitively`() {
        assertEquals(RunStores.Backend.FILE, RunStores.Backend.of("file"))
        assertEquals(RunStores.Backend.FILE, RunStores.Backend.of(" FILE "))
        assertEquals(RunStores.Backend.SQLITE, RunStores.Backend.of("SqLiTe"))
    }

    @Test
    fun `an unknown backend name fails loudly instead of falling back`() {
        // A typo would otherwise start the server against a store the operator did not choose, and they
        // would find out from missing history rather than from a message.
        val error = assertFailsWith<IllegalStateException> { RunStores.Backend.of("postgres") }
        assertTrue(error.message!!.contains("postgres"), "the message should name the bad value")
        assertTrue(error.message!!.contains("sqlite"), "the message should list what is valid")
    }

    @Test
    fun `the sqlite backend puts runs and logs in one database file`(@TempDir dir: Path) {
        val stores = RunStores.open(dir, RunStores.Backend.SQLITE)
        stores.runs.record(record("run-1"))
        stores.logs.append("run-1", "hello")

        assertTrue(dir.resolve(RunStores.DATABASE_FILE).exists())
        assertEquals("run-1", stores.runs.get("run-1")?.id)
        assertEquals(listOf("hello"), stores.logs.read("run-1"))
    }

    @Test
    fun `the file backend keeps the original layout`(@TempDir dir: Path) {
        val stores = RunStores.open(dir, RunStores.Backend.FILE)
        stores.runs.record(record("run-1"))
        stores.logs.append("run-1", "hello")

        assertTrue(dir.resolve("run-1.json").exists(), "one JSON file per run")
        assertTrue(dir.resolve(RunStores.LOG_DIR).resolve("run-1.log").exists(), "one log file per run")
        assertTrue(!dir.resolve(RunStores.DATABASE_FILE).exists(), "no database is created")
    }

    @Test
    fun `opening sqlite over an existing file history imports it, logs included`(@TempDir dir: Path) {
        // Turning the database on must not look like the history was lost.
        val files = RunStores.open(dir, RunStores.Backend.FILE)
        files.runs.record(record("run-old"))
        files.runs.record(record("run-new"))
        files.logs.append("run-old", "[build] compiling")

        val db = RunStores.open(dir, RunStores.Backend.SQLITE)

        assertEquals(setOf("run-old", "run-new"), db.runs.recent(10).map { it.id }.toSet())
        assertEquals(listOf("[build] compiling"), db.logs.read("run-old"))
    }

    @Test
    fun `the import runs once and never duplicates on a later start`(@TempDir dir: Path) {
        RunStores.open(dir, RunStores.Backend.FILE).runs.record(record("run-1"))

        RunStores.open(dir, RunStores.Backend.SQLITE)
        val reopened = RunStores.open(dir, RunStores.Backend.SQLITE)

        assertEquals(1, reopened.runs.recent(10).size)
    }

    @Test
    fun `a run recorded after the import is not overwritten by it`(@TempDir dir: Path) {
        // The guard is "the database is empty", so a second start must not re-copy the stale file copy
        // over a run the database has since recorded under the same id.
        RunStores.open(dir, RunStores.Backend.FILE).runs.record(record("run-1").copy(status = "Running"))
        RunStores.open(dir, RunStores.Backend.SQLITE)
        RunStores.open(dir, RunStores.Backend.SQLITE).runs.record(record("run-1").copy(status = "Success"))

        assertEquals("Success", RunStores.open(dir, RunStores.Backend.SQLITE).runs.get("run-1")?.status)
    }

    @Test
    fun `the import leaves the files alone so the file backend still reads them`(@TempDir dir: Path) {
        // What makes the switch reversible: setting the backend back to `file` returns to the old state.
        RunStores.open(dir, RunStores.Backend.FILE).runs.record(record("run-1"))
        RunStores.open(dir, RunStores.Backend.SQLITE)

        assertEquals(listOf("run-1"), dir.listDirectoryEntries("*.json").map { it.fileName.toString().removeSuffix(".json") })
        assertEquals("run-1", RunStores.open(dir, RunStores.Backend.FILE).runs.get("run-1")?.id)
    }

    @Test
    fun `imported runs keep the order the file store served them in`(@TempDir dir: Path) {
        val files = RunStores.open(dir, RunStores.Backend.FILE)
        listOf("a", "b", "c").forEach { files.runs.record(record(it)) }
        val fromFiles = files.runs.recent(10).map { it.id }

        assertEquals(fromFiles, RunStores.open(dir, RunStores.Backend.SQLITE).runs.recent(10).map { it.id })
    }

    @Test
    fun `opening an empty directory creates an empty history rather than failing`(@TempDir dir: Path) {
        val fresh = dir.resolve("does-not-exist-yet")

        assertTrue(RunStores.open(fresh, RunStores.Backend.SQLITE).runs.recent(10).isEmpty())
    }
}
