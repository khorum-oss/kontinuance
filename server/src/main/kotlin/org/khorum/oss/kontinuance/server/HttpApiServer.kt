package org.khorum.oss.kontinuance.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * Binds [RunApi] onto the JDK built-in [HttpServer] under `/api` (no third-party HTTP framework, no new
 * dependency). Routing only; all read logic stays in [RunApi]. An unknown path returns 404 and a
 * non-GET method on a known path returns 405 (FR-006); any handler error returns 500 rather than
 * crashing the server. [start]/[stop] manage the lifecycle; [boundPort] reports the actual port.
 *
 * @param api the read handlers.
 * @param host bind host (e.g. `127.0.0.1`).
 * @param port bind port; `0` picks an ephemeral port (see [boundPort]).
 */
class HttpApiServer(
    private val api: RunApi,
    host: String,
    port: Int,
) {
    private val server: HttpServer = HttpServer.create(InetSocketAddress(host, port), 0)

    /** The actual bound port (useful when constructed with port 0). */
    val boundPort: Int get() = server.address.port

    init {
        server.createContext("/") { exchange -> handle(exchange) }
    }

    /** Starts serving (non-blocking). */
    fun start() = server.start()

    /** Stops serving immediately. */
    fun stop() = server.stop(0)

    @Suppress("TooGenericExceptionCaught")
    private fun handle(exchange: HttpExchange) {
        val response = try {
            route(exchange)
        } catch (e: RuntimeException) {
            System.err.println("kontinuance-api: request failed: ${e.message}")
            ApiResponse(INTERNAL_ERROR, JsonView.message("error", "internal error"))
        }
        respond(exchange, response)
    }

    private fun route(exchange: HttpExchange): ApiResponse {
        val path = exchange.requestURI.path
        val known = path == HEALTH || path == RUNS || path.startsWith(RUN_PREFIX)
        if (!known) return ApiResponse(NOT_FOUND, JsonView.message("error", "not found"))
        if (exchange.requestMethod != "GET") {
            return ApiResponse(METHOD_NOT_ALLOWED, JsonView.message("error", "method not allowed"))
        }
        return when {
            path == HEALTH -> api.health()
            path == RUNS -> api.listRuns(limitParam(exchange.requestURI.rawQuery))
            else -> api.getRun(path.removePrefix(RUN_PREFIX))
        }
    }

    private fun limitParam(query: String?): Int? =
        query?.split("&")
            ?.firstOrNull { it.startsWith("limit=") }
            ?.removePrefix("limit=")
            ?.toIntOrNull()

    private fun respond(exchange: HttpExchange, response: ApiResponse) {
        val bytes = response.json.encodeToByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(response.status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val HEALTH = "/api/health"
        const val RUNS = "/api/runs"
        const val RUN_PREFIX = "/api/runs/"
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
        const val INTERNAL_ERROR = 500
    }
}
