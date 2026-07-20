package org.khorum.oss.kontinuance.persistence

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the durable [FileRunLogStore] (018): append→read order, per-run isolation, empty-for-unknown, and
 * verbatim round-trip of an already-masked line.
 */
class RunLogStoreTest {

    @Test
    fun `appends are read back in order`(@TempDir dir: Path) {
        val store = FileRunLogStore(dir)
        listOf("[build] compiling", "[build] done", "[test] 12 passed").forEach { store.append("run-1", it) }
        assertEquals(listOf("[build] compiling", "[build] done", "[test] 12 passed"), store.read("run-1"))
    }

    @Test
    fun `logs are isolated per run id`(@TempDir dir: Path) {
        val store = FileRunLogStore(dir)
        store.append("run-a", "a-line")
        store.append("run-b", "b-line")
        assertEquals(listOf("a-line"), store.read("run-a"))
        assertEquals(listOf("b-line"), store.read("run-b"))
    }

    @Test
    fun `an unknown run id reads as empty`(@TempDir dir: Path) {
        assertEquals(emptyList(), FileRunLogStore(dir).read("never-written"))
    }

    @Test
    fun `a masked line is stored verbatim`(@TempDir dir: Path) {
        val store = FileRunLogStore(dir)
        store.append("run-1", "[deploy] token=***")
        assertEquals(listOf("[deploy] token=***"), store.read("run-1"))
    }

    @Test
    fun `an id with filesystem-unsafe characters is stored and read`(@TempDir dir: Path) {
        val store = FileRunLogStore(dir)
        store.append("#KX/2046", "line")
        assertEquals(listOf("line"), store.read("#KX/2046"))
    }
}
