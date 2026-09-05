package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.config.EventSourceConfig
import org.khorum.oss.kontinuance.github.health.HeartbeatState
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.khorum.oss.kontinuance.server.domain.ConnectSourceRequest
import org.khorum.oss.kontinuance.server.domain.ErrorResponse
import org.khorum.oss.kontinuance.server.domain.SourceCursor
import org.khorum.oss.kontinuance.server.domain.SourceHeartbeat
import org.khorum.oss.kontinuance.server.domain.SourceRepo
import org.khorum.oss.kontinuance.server.domain.SourceResponse
import org.khorum.oss.kontinuance.server.service.AuthCredentials
import org.khorum.oss.kontinuance.server.service.GitHubSourceService
import org.khorum.oss.kontinuance.server.store.GitHubTokenStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * The GitHub event source (003) as the dashboard sees it: observable since 035/036, and since 040
 * **connectable** — a repository can be watched from the UI instead of by hand-writing a config YAML and
 * running the separate `kontinuance-ci` CLI beside the server.
 *
 * - `GET /api/source` reports the watched repositories, poll cadence, base URL, the token env-var **name**
 *   (never a token value), the poll cursors, liveness (036), and whether the server is polling right now.
 * - `POST /api/source` writes the config, stores the supplied token, and starts polling.
 * - `DELETE /api/source` stops polling; `?forget=true` also removes the config and the stored token.
 *
 * The write endpoints refuse to run on an **unauthenticated** server. They accept a credential and start
 * outbound work under it, which is not something an open API should let any caller on the network do; a
 * deployment that wants dashboard-driven setup configures the operator credential first. Reads keep their
 * previous behavior, so an open server still displays a source the CLI runs.
 */
@RestController
class GitHubSourceController(
    private val service: GitHubSourceService,
    private val tokens: GitHubTokenStore,
    private val credentials: AuthCredentials,
    @Value("\${kontinuance.github.cursors:#{null}}") cursorsPath: String?,
    @Value("\${kontinuance.github.heartbeat:#{null}}") heartbeatPath: String?,
) {
    private val cursors: Path = cursorsPath?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".kontinuance", "github-cursors.properties")
    private val heartbeat: Path = heartbeatPath?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".kontinuance", "github-heartbeat.properties")

    @GetMapping("/api/source")
    suspend fun source(): ResponseEntity<*> = withContext(Dispatchers.IO) {
        val path = service.config.takeIf { Files.isRegularFile(it) }
            ?: return@withContext ResponseEntity.ok(
                SourceResponse(configured = false, manageable = credentials.enabled),
            )

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
                running = service.running,
                hasToken = tokens.resolve(parsed.tokenEnv) != null,
                manageable = credentials.enabled,
            ),
        )
    }

    /** Connects (or reconnects) the event source to a repository and starts polling. */
    @PostMapping("/api/source")
    suspend fun connect(@RequestBody request: ConnectSourceRequest): ResponseEntity<*> {
        requireManageable()?.let { return it }

        val config = try {
            configFrom(request)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(ErrorResponse(e.message ?: "invalid event-source request"))
        }

        return try {
            service.connect(config, request.token)
            source()
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(ErrorResponse(e.message ?: "could not connect the event source"))
        }
    }

    /** Stops polling; [forget] also removes the config and the stored token. */
    @DeleteMapping("/api/source")
    suspend fun disconnect(@RequestParam(defaultValue = "false") forget: Boolean): ResponseEntity<*> {
        requireManageable()?.let { return it }
        if (forget) service.forget() else service.disconnect()
        return source()
    }

    // --- internals ------------------------------------------------------------------------------------

    // Write endpoints are refused on an open server; see the class comment.
    private fun requireManageable(): ResponseEntity<*>? =
        if (credentials.enabled) {
            null
        } else {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(
                    "Connecting a GitHub source requires operator authentication. Set " +
                        "KONTINUANCE_AUTH_USERNAME and KONTINUANCE_AUTH_PASSWORD, then try again.",
                ),
            )
        }

    private fun configFrom(request: ConnectSourceRequest): EventSourceConfig {
        val owner = request.owner.trim()
        val name = request.name.trim()
        val prPipeline = request.prPipeline.trim()
        require(owner.isNotEmpty()) { "owner is required" }
        require(name.isNotEmpty()) { "name is required" }
        require(prPipeline.isNotEmpty()) { "prPipeline is required" }
        val interval = request.pollIntervalSeconds ?: DEFAULT_POLL_SECONDS
        require(interval >= MIN_POLL_SECONDS) { "pollIntervalSeconds must be at least $MIN_POLL_SECONDS" }

        return EventSourceConfig(
            tokenEnv = request.tokenEnv?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_TOKEN_ENV,
            pollIntervalSeconds = interval,
            baseUrl = request.baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_BASE_URL,
            bindings = listOf(
                RepositoryBinding(
                    repo = RepoRef(owner, name),
                    prPipeline = Path.of(prPipeline),
                    pushPipeline = request.pushPipeline?.trim()?.takeIf { it.isNotEmpty() }?.let { Path.of(it) },
                    trackedBranch = request.trackedBranch?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_BRANCH,
                ),
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

        private const val DEFAULT_TOKEN_ENV = "GITHUB_TOKEN"
        private const val DEFAULT_BASE_URL = "https://api.github.com"
        private const val DEFAULT_BRANCH = "main"
        private const val DEFAULT_POLL_SECONDS = 60L

        // GitHub's REST rate limit is per hour; polling faster than this burns it for no added freshness.
        private const val MIN_POLL_SECONDS = 10L
    }
}
