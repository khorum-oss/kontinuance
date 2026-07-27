package org.khorum.oss.kontinuance.server

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit-tests the suspending [RunReadFacade] in isolation: each suspend method returns the same typed value
 * as the underlying [RunApi], exercising the `withContext(Dispatchers.IO)` offload boundary (FR-003 /
 * SC-003) without a running server. No duplicated read logic — the facade only offloads (FR-004 / SC-006).
 */
class RunReadFacadeTest {

    private fun facadeWith(vararg ids: String): RunReadFacade {
        val store = InMemoryRunStore().apply {
            ids.forEach { record(RunRecord(id = it, pipeline = "p", status = "Success")) }
        }
        return RunReadFacade(RunApi(store))
    }

    @Test
    fun `health suspends and returns ok`() = runTest {
        assertEquals("ok", facadeWith().health().status)
    }

    @Test
    fun `list suspends and matches the underlying RunApi newest-first`() = runTest {
        assertEquals(listOf("c", "b", "a"), facadeWith("a", "b", "c").listRuns(limit = null).runs.map { it.id })
    }

    @Test
    fun `get suspends and returns null for an unknown id`() = runTest {
        val facade = facadeWith("run-7")
        assertEquals("run-7", facade.getRun("run-7")?.id)
        assertNull(facade.getRun("nope"))
    }
}
