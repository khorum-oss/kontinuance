package org.khorum.oss.kontinuance.github.support

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * A tiny in-process stand-in for the GitHub REST API, built on the JDK's built-in [HttpServer] — no
 * third-party mock-server dependency. Register responders by (method, path-regex); every request is
 * recorded so tests can assert what the client sent. Bound to loopback on an ephemeral port.
 */
class FakeGitHubServer : AutoCloseable {

    /** A request the server received. [path] excludes the query string; [rawUri] includes it. */
    data class Recorded(
        val method: String,
        val path: String,
        val rawUri: String,
        val body: String,
        val authorization: String?,
    )

    private data class Rule(val method: String, val path: Regex, val status: Int, val body: String)

    val requests: MutableList<Recorded> = mutableListOf()
    private val rules = mutableListOf<Rule>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    /** The base URL to hand to `RestGitHubClient(baseUrl = ...)`. */
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            val path = exchange.requestURI.path
            val auth = exchange.requestHeaders.getFirst("Authorization")
            requests += Recorded(exchange.requestMethod, path, exchange.requestURI.toString(), body, auth)
            val rule = rules.firstOrNull { it.method == exchange.requestMethod && it.path.matches(path) }
            val status = rule?.status ?: NOT_FOUND
            val payload = (rule?.body ?: """{"message":"no rule"}""").encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
    }

    /** Registers a responder: requests matching [method] + [pathRegex] get [status] and [body]. */
    fun on(method: String, pathRegex: String, status: Int = OK, body: String = "{}"): FakeGitHubServer {
        rules += Rule(method, Regex(pathRegex), status, body)
        return this
    }

    override fun close() = server.stop(0)

    private companion object {
        const val OK = 200
        const val NOT_FOUND = 404
    }
}
