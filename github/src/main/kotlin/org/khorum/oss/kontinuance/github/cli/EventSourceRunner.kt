package org.khorum.oss.kontinuance.github.cli

import kotlinx.coroutines.runBlocking
import org.khorum.oss.kontinuance.github.EventSource
import org.khorum.oss.kontinuance.github.client.RestGitHubClient
import org.khorum.oss.kontinuance.github.config.EventSourceConfig
import org.khorum.oss.kontinuance.github.poll.CursorStore
import org.khorum.oss.kontinuance.github.poll.FileCursorStore
import org.khorum.oss.kontinuance.github.poll.Poller
import org.khorum.oss.kontinuance.github.report.RunReporter
import org.khorum.oss.kontinuance.github.trigger.TriggerResolver
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import java.io.IOException
import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = "usage: kontinuance-ci <config.yaml>"
private const val MILLIS_PER_SECOND = 1000L

/**
 * Wires an [EventSource] from [config]: a REST client authenticated by the token in the env var named
 * by `config.tokenEnv` (read from [env], never inlined — Constitution V), a poller over the configured
 * bindings backed by [cursors], a reporter, and the default engine. Extracted from [main] so it is unit
 * testable without a process.
 */
fun eventSourceFrom(
    config: EventSourceConfig,
    cursors: CursorStore,
    env: (String) -> String? = System::getenv,
): EventSource {
    val token = env(config.tokenEnv)?.takeIf { it.isNotBlank() }
        ?: error("token env var '${config.tokenEnv}' is not set")
    val client = RestGitHubClient(token = token, baseUrl = config.baseUrl)
    return EventSource(
        poller = Poller(client, config.bindings, cursors),
        resolver = TriggerResolver(config.bindings),
        reporter = RunReporter(client),
    )
}

/** Service entrypoint: load the native config, then poll GitHub forever on the configured cadence. */
fun main(args: Array<String>) {
    val configPath = args.firstOrNull() ?: fail(USAGE)
    val config = loadConfig(configPath)
    val cursorFile = Path.of(System.getProperty("user.home"), ".kontinuance", "github-cursors.properties")
    val source = try {
        eventSourceFrom(config, FileCursorStore(cursorFile))
    } catch (e: IllegalStateException) {
        fail(e.message)
    }
    println("kontinuance-ci: watching ${config.bindings.size} repo(s), polling every ${config.pollIntervalSeconds}s")
    runBlocking { source.runForever(config.pollIntervalSeconds * MILLIS_PER_SECOND) }
}

private fun loadConfig(path: String): EventSourceConfig =
    try {
        EventSourceConfig.load(Path.of(path))
    } catch (e: IOException) {
        fail("cannot read config '$path': ${e.message}")
    } catch (e: IllegalArgumentException) {
        fail("invalid config '$path': ${e.message}")
    } catch (e: IllegalStateException) {
        fail("invalid config '$path': ${e.message}")
    } catch (e: YamlEngineException) {
        fail("invalid config '$path': ${e.message}")
    }

private fun fail(message: String?): Nothing {
    System.err.println("kontinuance-ci: ${message ?: "error"}")
    exitProcess(2)
}
