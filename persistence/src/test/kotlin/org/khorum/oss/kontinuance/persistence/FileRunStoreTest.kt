package org.khorum.oss.kontinuance.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileRunStoreTest {

    private fun record(id: String) = RunRecord(id = id, pipeline = "p", status = "Success")

    @Test
    fun `records and fetches a run by id`(@TempDir dir: Path) {
        val store = FileRunStore(dir)
        store.record(record("run-1"))
        assertEquals("run-1", store.get("run-1")?.id)
        assertNull(store.get("missing"))
    }

    @Test
    fun `records survive a restart (new store over the same dir)`(@TempDir dir: Path) {
        FileRunStore(dir).record(record("run-durable"))

        val reopened = FileRunStore(dir)
        assertEquals("run-durable", reopened.get("run-durable")?.id)
    }

    @Test
    fun `recent lists newest-first and honors the limit`(@TempDir dir: Path) {
        val store = FileRunStore(dir)
        listOf("a", "b", "c").forEach { store.record(record(it)) }
        // Set explicit, increasing mtimes so ordering is deterministic regardless of FS granularity.
        setMtime(dir, "a", epochMillis = 1_000)
        setMtime(dir, "b", epochMillis = 2_000)
        setMtime(dir, "c", epochMillis = 3_000)

        val recent = store.recent(2)
        assertEquals(2, recent.size)
        assertEquals(listOf("c", "b"), recent.map { it.id })
    }

    private fun setMtime(dir: Path, id: String, epochMillis: Long) {
        Files.setLastModifiedTime(dir.resolve("$id.json"), FileTime.fromMillis(epochMillis))
    }

    @Test
    fun `a corrupt record is skipped, not fatal`(@TempDir dir: Path) {
        val store = FileRunStore(dir)
        store.record(record("good"))
        dir.resolve("corrupt.json").writeText("{ this is not valid json ")

        val recent = store.recent(10)
        assertEquals(listOf("good"), recent.map { it.id }, "the valid record still loads")
    }
}
