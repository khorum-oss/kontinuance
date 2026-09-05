package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.khorum.oss.kontinuance.github.cli.eventSourceFrom
import org.khorum.oss.kontinuance.github.config.EventSourceConfig
import org.khorum.oss.kontinuance.github.health.FileHeartbeat
import org.khorum.oss.kontinuance.github.poll.FileCursorStore
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.store.GitHubTokenStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeText

/**
 * Owns the GitHub event source's lifecycle inside the server process (040).
 *
 * Before this, watching a repository meant running the separate `kontinuance-ci` CLI: a hand-written config
 * YAML, a token in that process's environment, and its own cursor and heartbeat files, with the server only
 * *reading* that state to display it. The poll loop is a plain coroutine
 * ([org.khorum.oss.kontinuance.github.EventSource.runForever]) with no CLI in it, so the server can host it
 * directly — one process to run, and a repository can be connected from the dashboard.
 *
 * The on-disk contract is unchanged: the same config YAML, cursor file, and heartbeat file, in the same
 * places. A deployment already running the CLI keeps working, and one that connects through the dashboard
 * produces a config the CLI could run. What this adds is a writer and a supervisor for them.
 *
 * Only one source runs at a time. [connect] replaces whatever is running; [disconnect] stops it and leaves
 * the config in place so it can be restarted; [forget] removes the config and the stored token.
 */
@Service
class GitHubSourceService(
    private val runStore: RunStore,
    private val tokens: GitHubTokenStore,
    @Value("\${kontinuance.github.config:#{null}}") configPath: String?,
    @Value("\${kontinuance.github.cursors:#{null}}") cursorsPath: String?,
    @Value("\${kontinuance.github.heartbeat:#{null}}") heartbeatPath: String?,
    @param:Value("\${kontinuance.github.autostart:true}") private val autostart: Boolean,
) : DisposableBean {

    private val stateDir: Path = Path.of(System.getProperty("user.home"), ".kontinuance")

    /** The event source's config file — the one the CLI reads and `/api/source` already displays. */
    val config: Path = configPath?.let { Path.of(it) } ?: stateDir.resolve("github-source.yaml")
    private val cursors: Path = cursorsPath?.let { Path.of(it) } ?: stateDir.resolve("github-cursors.properties")
    private val heartbeat: Path = heartbeatPath?.let { Path.of(it) } ?: stateDir.resolve("github-heartbeat.properties")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("github-event-source"))
    private val mutex = Mutex()

    @Volatile
    private var job: Job? = null

    /** True while a poll loop is running. */
    val running: Boolean get() = job?.isActive == true

    /**
     * Writes [config] to disk, stores [token] when one was supplied, and starts polling. Replaces any
     * running source. Throws [IllegalStateException] when no token can be resolved, so a caller reports the
     * missing credential instead of starting a loop that would 401 on every cycle.
     */
    suspend fun connect(config: EventSourceConfig, token: String?) {
        mutex.withLock {
            stop()
            token?.takeIf { it.isNotBlank() }?.let { tokens.store(it) }
            check(tokens.resolve(config.tokenEnv) != null) {
                "no GitHub token available: supply one, or set the '${config.tokenEnv}' environment variable"
            }
            writeConfig(config)
            start(config)
        }
    }

    /** Stops polling. The config stays on disk, so [restart] can bring the same source back. */
    suspend fun disconnect() {
        mutex.withLock { stop() }
    }

    /** Stops polling and removes the config and any stored token. */
    suspend fun forget() {
        mutex.withLock {
            stop()
            tokens.clear()
            runCatching { java.nio.file.Files.deleteIfExists(config) }
        }
    }

    /** Starts the source from the config already on disk. No-op when there is no config or no token. */
    suspend fun restart(): Boolean = mutex.withLock {
        stop()
        val parsed = readConfig() ?: return@withLock false
        if (tokens.resolve(parsed.tokenEnv) == null) return@withLock false
        start(parsed)
        true
    }

    /** Starts a previously-configured source at boot, so a restart resumes watching without a UI visit. */
    @EventListener(ContextRefreshedEvent::class)
    fun onStartup() {
        if (!autostart || !config.isRegularFile()) return
        scope.launch {
            val started = runCatching { restart() }.getOrDefault(false)
            if (!started) {
                log.warn(
                    "A GitHub event-source config exists at {} but the source did not start — most likely no " +
                        "token is available. Reconnect from the dashboard, or set the token environment variable.",
                    config,
                )
            }
        }
    }

    override fun destroy() {
        scope.coroutineContext[Job]?.cancel()
    }

    // --- internals ------------------------------------------------------------------------------------

    private fun readConfig(): EventSourceConfig? =
        config.takeIf { it.isRegularFile() }?.let { path -> runCatching { EventSourceConfig.load(path) }.getOrNull() }

    private fun writeConfig(value: EventSourceConfig) {
        config.parent?.createDirectories()
        config.writeText(value.render())
    }

    // Callers hold the mutex. Wiring failures (a token that vanished between the check and here) surface to
    // the caller rather than leaving a half-started source behind.
    private fun start(value: EventSourceConfig) {
        val source = eventSourceFrom(
            config = value,
            cursors = FileCursorStore(cursors),
            runStore = runStore,
            heartbeat = FileHeartbeat(heartbeat),
            env = { name -> tokens.resolve(name) },
        )
        log.info(
            "GitHub event source started: watching {} repo(s), polling every {}s",
            value.bindings.size,
            value.pollIntervalSeconds,
        )
        job = scope.launch { source.runForever(value.pollIntervalSeconds * MILLIS_PER_SECOND) }
    }

    // Callers hold the mutex. Waits for the loop to actually end so a reconnect never overlaps a poll.
    private suspend fun stop() {
        job?.cancelAndJoin()
        job = null
    }

    private companion object {
        private val log = LoggerFactory.getLogger(GitHubSourceService::class.java)
        private const val MILLIS_PER_SECOND = 1000L
    }
}
