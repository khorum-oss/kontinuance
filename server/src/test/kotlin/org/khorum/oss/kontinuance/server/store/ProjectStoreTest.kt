package org.khorum.oss.kontinuance.server.store

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.server.domain.project.ProjectSource
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit-tests the file-backed [ProjectStore] (032): round-tripping descriptor text by name, the active
 * marker, and the slug validation that keeps a name from ever escaping the store directory.
 */
class ProjectStoreTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `save then get round-trips the descriptor text`() {
        val store = ProjectStore(dir)
        store.save("alpha", "pipeline: {}\n")

        assertTrue(store.exists("alpha"))
        assertEquals("pipeline: {}\n", store.get("alpha"))
    }

    @Test
    fun `get returns null and exists is false for an unknown project`() {
        val store = ProjectStore(dir)

        assertNull(store.get("missing"))
        assertFalse(store.exists("missing"))
    }

    @Test
    fun `list returns the registered names sorted, without the yml suffix`() {
        val store = ProjectStore(dir)
        store.save("gamma", "g")
        store.save("alpha", "a")
        store.save("beta", "b")

        assertEquals(listOf("alpha", "beta", "gamma"), store.list())
    }

    @Test
    fun `save replaces an existing project's text`() {
        val store = ProjectStore(dir)
        store.save("svc", "one")
        store.save("svc", "two")

        assertEquals(listOf("svc"), store.list())
        assertEquals("two", store.get("svc"))
    }

    @Test
    fun `activeName is null until set, then reflects setActive`() {
        val store = ProjectStore(dir)
        assertNull(store.activeName())

        store.save("svc", "x")
        store.setActive("svc")
        assertEquals("svc", store.activeName())
    }

    @Test
    fun `the active marker is not listed as a project`() {
        val store = ProjectStore(dir)
        store.save("svc", "x")
        store.setActive("svc")

        assertEquals(listOf("svc"), store.list())
    }

    @Test
    fun `saveSource then source round-trips the repo and branch`() {
        val store = ProjectStore(dir)
        store.save("svc", "x")
        store.saveSource("svc", ProjectSource("https://example.test/svc", "main"))

        val src = store.source("svc")
        assertEquals("https://example.test/svc", src?.repo)
        assertEquals("main", src?.branch)
    }

    @Test
    fun `source is null when a project has no source sidecar`() {
        val store = ProjectStore(dir)
        store.save("svc", "x")
        assertNull(store.source("svc"))
    }

    @Test
    fun `a source with a repo but no branch round-trips with a null branch`() {
        val store = ProjectStore(dir)
        store.save("svc", "x")
        store.saveSource("svc", ProjectSource("https://example.test/svc"))

        val src = store.source("svc")
        assertEquals("https://example.test/svc", src?.repo)
        assertNull(src?.branch)
    }

    @Test
    fun `saving a blank repo clears the source`() {
        val store = ProjectStore(dir)
        store.save("svc", "x")
        store.saveSource("svc", ProjectSource("https://example.test/svc", "main"))
        store.saveSource("svc", ProjectSource("  ", "main"))

        assertNull(store.source("svc"))
    }

    @Test
    fun `the source sidecar is not listed as a project`() {
        val store = ProjectStore(dir)
        store.save("svc", "x")
        store.saveSource("svc", ProjectSource("https://example.test/svc", "main"))

        assertEquals(listOf("svc"), store.list())
    }

    @Test
    fun `isValidName accepts slugs and rejects paths and empties`() {
        assertTrue(ProjectStore.isValidName("kontinuance-service"))
        assertTrue(ProjectStore.isValidName("infra_charts.v2"))
        assertFalse(ProjectStore.isValidName(""))
        assertFalse(ProjectStore.isValidName("../escape"))
        assertFalse(ProjectStore.isValidName("has/slash"))
        assertFalse(ProjectStore.isValidName("with space"))
        assertFalse(ProjectStore.isValidName("x".repeat(65)))
    }
}
