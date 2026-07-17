package org.khorum.oss.kontinuance.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunApiTest {

    private fun storeWith(vararg ids: String) = InMemoryRunStore().apply {
        ids.forEach { record(RunRecord(id = it, pipeline = "p", status = "Success")) }
    }

    private fun runIds(json: String): List<String> =
        Json.parseToJsonElement(json).jsonObject.getValue("runs").jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }

    @Test
    fun `health reports ok`() {
        val response = RunApi(InMemoryRunStore()).health()
        assertEquals(200, response.status)
        assertTrue(response.json.contains("\"status\":\"ok\""))
    }

    @Test
    fun `list returns runs newest-first`() {
        val response = RunApi(storeWith("a", "b", "c")).listRuns(limit = null)
        assertEquals(200, response.status)
        assertEquals(listOf("c", "b", "a"), runIds(response.json))
    }

    @Test
    fun `empty store lists an empty runs array`() {
        val response = RunApi(InMemoryRunStore()).listRuns(limit = null)
        assertEquals("""{"runs":[]}""", response.json)
    }

    @Test
    fun `limit defaults, clamps to the max, and honors an explicit value`() {
        val api = RunApi(storeWith("a", "b", "c", "d", "e"), defaultLimit = 2, maxLimit = 3)
        assertEquals(2, runIds(api.listRuns(limit = null).json).size, "absent -> default")
        assertEquals(2, runIds(api.listRuns(limit = 0).json).size, "invalid -> default")
        assertEquals(3, runIds(api.listRuns(limit = 100).json).size, "over cap -> max")
        assertEquals(1, runIds(api.listRuns(limit = 1).json).size, "explicit honored")
    }

    @Test
    fun `get returns a known run and 404 for an unknown id`() {
        val api = RunApi(storeWith("run-7"))
        val found = api.getRun("run-7")
        assertEquals(200, found.status)
        assertTrue(found.json.contains("\"id\":\"run-7\""))

        val missing = api.getRun("nope")
        assertEquals(404, missing.status)
        assertTrue(missing.json.contains("not found"))
    }
}
