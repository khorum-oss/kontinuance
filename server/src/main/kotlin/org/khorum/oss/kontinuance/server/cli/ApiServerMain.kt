package org.khorum.oss.kontinuance.server.cli

import org.khorum.oss.kontinuance.persistence.FileRunStore
import org.khorum.oss.kontinuance.server.HttpApiServer
import org.khorum.oss.kontinuance.server.RunApi
import java.io.IOException
import java.nio.file.Path
import kotlin.system.exitProcess

private const val DEFAULT_HOST = "127.0.0.1"
private const val DEFAULT_PORT = 8077
private const val USAGE = "usage: kontinuance-api [--host H] [--port N] [--store DIR]"

/**
 * Launches the read API over the CI service's persisted run store. Host/port come from `--host`/`--port`
 * or `KONTINUANCE_API_HOST`/`KONTINUANCE_API_PORT` (default `127.0.0.1:8077`); the store dir from
 * `--store` (default `~/.kontinuance/runs`). Exits with a clear message if it cannot bind (FR-007).
 */
fun main(args: Array<String>) {
    val opts = parseArgs(args)
    val host = opts["host"] ?: System.getenv("KONTINUANCE_API_HOST") ?: DEFAULT_HOST
    val port = (opts["port"] ?: System.getenv("KONTINUANCE_API_PORT"))?.toIntOrNull() ?: DEFAULT_PORT
    val storeDir = opts["store"]?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".kontinuance", "runs")

    val server = try {
        HttpApiServer(RunApi(FileRunStore(storeDir)), host, port)
    } catch (e: IOException) {
        System.err.println("kontinuance-api: cannot bind $host:$port: ${e.message}")
        exitProcess(2)
    }
    server.start()
    println("kontinuance-api: serving $storeDir on http://$host:${server.boundPort}/api")
    Thread.currentThread().join()
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val opts = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i].removePrefix("--")
        val value = args.getOrNull(i + 1)
        if (args[i].startsWith("--") && value != null && key in KNOWN) {
            opts[key] = value
            i += 2
        } else {
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
    return opts
}

private val KNOWN = setOf("host", "port", "store")
