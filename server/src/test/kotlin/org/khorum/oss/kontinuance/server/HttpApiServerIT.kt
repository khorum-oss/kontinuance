package org.khorum.oss.kontinuance.server

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [HttpApiServer] over a **real HTTP round-trip** with the JDK `HttpClient` against a started
 * server bound to loopback — the real transport boundary (Constitution II), no mock-server dependency.
 */
class HttpApiServerIT {

    private fun withServer(vararg ids: String, block: (Int) -> Unit) {
        val store = InMemoryRunStore().apply { ids.forEach { record(RunRecord(id = it, pipeline = "p", status = "Success")) } }
        val server = HttpApiServer(RunApi(store), "127.0.0.1", 0)
        server.start()
        try {
            block(server.boundPort)
        } finally {
            server.stop()
        }
    }

    private fun send(port: Int, path: String, method: String = "GET"): Pair<Int, String> {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build()
        val response = HttpClient.newHttpClient().send(request, BodyHandlers.ofString())
        return response.statusCode() to response.body()
    }

    @Test
    fun `health, list, and detail round-trip over HTTP`() = withServer("a", "b", "c") { port ->
        val (healthCode, healthBody) = send(port, "/api/health")
        assertEquals(200, healthCode)
        assertTrue(healthBody.contains("\"status\":\"ok\""))

        val (listCode, listBody) = send(port, "/api/runs?limit=2")
        assertEquals(200, listCode)
        assertTrue(listBody.startsWith("{\"runs\":["))
        assertTrue(listBody.contains("\"id\":\"c\"") && listBody.contains("\"id\":\"b\""))
        assertTrue(!listBody.contains("\"id\":\"a\""), "limit=2 excludes the oldest")

        val (runCode, runBody) = send(port, "/api/runs/a")
        assertEquals(200, runCode)
        assertTrue(runBody.contains("\"id\":\"a\""))
    }

    @Test
    fun `unknown id is 404, unknown route is 404, bad method is 405`() = withServer("a") { port ->
        assertEquals(404, send(port, "/api/runs/missing").first)
        assertEquals(404, send(port, "/nope").first)
        assertEquals(405, send(port, "/api/health", method = "POST").first)
    }
}
