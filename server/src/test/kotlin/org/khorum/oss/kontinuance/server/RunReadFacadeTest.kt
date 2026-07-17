package org.khorum.oss.kontinuance.server

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import kotlin.test.assertEquals

/**
 * Unit-tests the suspending [RunReadFacade] in isolation: each suspend method returns the same
 * [ApiResponse] as the underlying [RunApi], exercising the `withContext(Dispatchers.IO)` offload
 * boundary (FR-003 / SC-003) without a running server. No duplicated read logic — the facade only
 * offloads (FR-004 / SC-006).
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
        val response = facadeWith().health()
        assertEquals(200, response.status)
        assertEquals(RunApi(InMemoryRunStore()).health().json, response.json)
    }

    @Test
    fun `list suspends and matches the underlying RunApi newest-first`() = runTest {
        val response = facadeWith("a", "b", "c").listRuns(limit = null)
        assertEquals(200, response.status)
        assertEquals("""{"runs":[""", response.json.substring(0, 9))
        assertEquals(true, response.json.indexOf("\"id\":\"c\"") < response.json.indexOf("\"id\":\"a\""))
    }

    @Test
    fun `get suspends and returns 404 for an unknown id`() = runTest {
        val facade = facadeWith("run-7")
        assertEquals(200, facade.getRun("run-7").status)
        assertEquals(404, facade.getRun("nope").status)
    }
}
