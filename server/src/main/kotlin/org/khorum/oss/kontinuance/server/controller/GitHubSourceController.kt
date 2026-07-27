package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.github.config.EventSourceConfig
import org.khorum.oss.kontinuance.github.health.HeartbeatState
import org.khorum.oss.kontinuance.server.domain.ErrorResponse
import org.khorum.oss.kontinuance.server.domain.SourceCursor
import org.khorum.oss.kontinuance.server.domain.SourceHeartbeat
import org.khorum.oss.kontinuance.server.domain.SourceRepo
import org.khorum.oss.kontinuance.server.domain.SourceResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Read-only view of the GitHub event source (003) for the dashboard (035/036). The event source runs as a
 * separate `kontinuance-ci` CLI; this endpoint makes it *observable* by reading the same on-disk state:
 *
 * - its **config** YAML (`kontinuance.github.config`), loaded with the event source's own
 *   [EventSourceConfig] parser — the watched repositories, poll cadence, base URL, and the token env-var
 *   **name** (never a token value; the config never stores one);
 * - its **poll cursors** (`kontinuance.github.cursors`, default `~/.kontinuance/github-cursors.properties`) —
 *   each PR/branch key and the last commit SHA the poller has processed;
 * - its **liveness** (`kontinuance.github.heartbeat`, 036) — the last successful poll, its age, whether it
 *   has gone stale, and the cycle count.
 *
 * When no config path is set (or the file is absent) it answers `{ "configured": false }`. It never starts,
 * stops, or reconfigures the event source — the CLI stays the runtime. Handlers return a typed DTO the
 * Jackson codec serializes.
 */
@RestController
class GitHubSourceController(
    @Value("\${kontinuance.github.config:#{null}}") configPath: String?,
    @Value("\${kontinuance.github.cursors:#{null}}") cursorsPath: String?,
    @Value("\${kontinuance.github.heartbeat:#{null}}") heartbeatPath: String?,
) {
    private val config: Path? = configPath?.let { Path.of(it) }
    private val cursors: Path = cursorsPath?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".kontinuance", "github-cursors.properties")
    private val heartbeat: Path = heartbeatPath?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".kontinuance", "github-heartbeat.properties")

    @GetMapping("/api/source")
    suspend fun source(): ResponseEntity<*> = withContext(Dispatchers.IO) {
        val path = config?.takeIf { Files.isRegularFile(it) }
            ?: return@withContext ResponseEntity.ok(SourceResponse(configured = false))

        val parsed = runCatching { EventSourceConfig.load(path) }
            .getOrElse {
                return@withContext ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse(it.message ?: "could not read the event-source config"))
            }

        ResponseEntity.ok(
            SourceResponse(
                configured = true,
                pollIntervalSeconds = parsed.pollIntervalSeconds,
                baseUrl = parsed.baseUrl,
                tokenEnv = parsed.tokenEnv, // the env-var NAME only — never a token value
                repositories = parsed.bindings.map { binding ->
                    SourceRepo(
                        slug = binding.repo.slug,
                        prPipeline = binding.prPipeline.toString(),
                        pushPipeline = binding.pushPipeline?.toString(),
                        trackedBranch = binding.trackedBranch,
                    )
                },
                cursors = readCursors().map { (key, sha) -> SourceCursor(key, sha) },
                heartbeat = readHeartbeat(parsed.pollIntervalSeconds),
            ),
        )
    }

    // Liveness (036): the last successful poll, its age, whether it has gone stale, and the cycle count;
    // null when the poller has never written a heartbeat (liveness unknown → omitted from the response).
    private fun readHeartbeat(pollIntervalSeconds: Long): SourceHeartbeat? {
        val state = HeartbeatState.read(heartbeat) ?: return null
        val ageSeconds = maxOf(0L, (System.currentTimeMillis() - state.lastPolledMillis) / MILLIS_PER_SECOND)
        return SourceHeartbeat(
            lastPolledMillis = state.lastPolledMillis,
            ageSeconds = ageSeconds,
            stale = ageSeconds > STALE_FACTOR * pollIntervalSeconds,
            cycles = state.cycles,
        )
    }

    // Read the poller's cursor properties (key = last-seen SHA), sorted; empty when the file is absent.
    private fun readCursors(): List<Pair<String, String>> {
        if (!Files.isRegularFile(cursors)) return emptyList()
        val props = Properties()
        runCatching { Files.newInputStream(cursors).use { props.load(it) } }.getOrElse { return emptyList() }
        return props.stringPropertyNames()
            .sorted()
            .mapNotNull { key -> props.getProperty(key)?.let { key to it } }
    }

    private companion object {
        private const val MILLIS_PER_SECOND = 1000L
        // A heartbeat older than this many poll intervals is "stale" — one missed poll is jitter, several
        // is a problem.
        private const val STALE_FACTOR = 3L
    }
}
