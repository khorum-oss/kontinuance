package org.khorum.oss.kontinuance.server.domain

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunApiTest {

    private fun storeWith(vararg ids: String) = InMemoryRunStore().apply {
        ids.forEach { record(RunRecord(id = it, pipeline = "p", status = "Success")) }
    }

    @Test
    fun `health reports ok`() {
        assertEquals("ok", RunApi(InMemoryRunStore()).health().status)
    }

    @Test
    fun `list returns runs newest-first`() {
        val runs = RunApi(storeWith("a", "b", "c")).listRuns(limit = null).runs
        assertEquals(listOf("c", "b", "a"), runs.map { it.id })
    }

    @Test
    fun `empty store lists an empty runs array`() {
        assertEquals(emptyList(), RunApi(InMemoryRunStore()).listRuns(limit = null).runs)
    }

    @Test
    fun `limit defaults, clamps to the max, and honors an explicit value`() {
        val api = RunApi(storeWith("a", "b", "c", "d", "e"), defaultLimit = 2, maxLimit = 3)
        assertEquals(2, api.listRuns(limit = null).runs.size, "absent -> default")
        assertEquals(2, api.listRuns(limit = 0).runs.size, "invalid -> default")
        assertEquals(3, api.listRuns(limit = 100).runs.size, "over cap -> max")
        assertEquals(1, api.listRuns(limit = 1).runs.size, "explicit honored")
    }

    @Test
    fun `get returns a known run and null for an unknown id`() {
        val api = RunApi(storeWith("run-7"))
        assertEquals("run-7", api.getRun("run-7")?.id)
        assertNull(api.getRun("nope"))
    }
}
