package org.khorum.oss.kontinuance.server.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.khorum.oss.kontinuance.github.config.EventSourceConfig
import org.khorum.oss.kontinuance.github.health.HeartbeatState
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Read-only view of the GitHub event source (003) for the dashboard (035). The event source runs as a
 * separate `kontinuance-ci` CLI; this endpoint makes it *observable* by reading the same on-disk state:
 *
 * - its **config** YAML (`kontinuance.github.config`), loaded with the event source's own
 *   [EventSourceConfig] parser — the watched repositories, poll cadence, base URL, and the token env-var
 *   **name** (never a token value; the config never stores one);
 * - its **poll cursors** (`kontinuance.github.cursors`, default `~/.kontinuance/github-cursors.properties`) —
 *   each PR/branch key and the last commit SHA the poller has processed.
 *
 * When no config path is set (or the file is absent) it answers `{ "configured": false }`. It never starts,
 * stops, or reconfigures the event source — the CLI stays the runtime.
 */
@RestController
class GitHubSourceController(
    @param:Value("\${kontinuance.github.config:#{null}}") configPath: String?,
    @param:Value("\${kontinuance.github.cursors:#{null}}") cursorsPath: String?,
    @param:Value("\${kontinuance.github.heartbeat:#{null}}") heartbeatPath: String?,
) {
    private val config: Path? = configPath?.let { Path.of(it) }
    private val cursors: Path = cursorsPath?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".kontinuance", "github-cursors.properties")
    private val heartbeat: Path = heartbeatPath?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".kontinuance", "github-heartbeat.properties")

    @GetMapping("/api/source")
    suspend fun source(): ResponseEntity<ByteArray> = withContext(Dispatchers.IO) {
        val path = config?.takeIf { Files.isRegularFile(it) }
            ?: return@withContext ok(buildJsonObject { put("configured", false) }.toString())

        val parsed = runCatching { EventSourceConfig.load(path) }
            .getOrElse { return@withContext error(it.message ?: "could not read the event-source config") }

        val json = buildJsonObject {
            put("configured", true)
            put("pollIntervalSeconds", parsed.pollIntervalSeconds)
            put("baseUrl", parsed.baseUrl)
            put("tokenEnv", parsed.tokenEnv) // the env-var NAME only — never a token value
            putJsonArray("repositories") {
                parsed.bindings.forEach { binding ->
                    addJsonObject {
                        put("slug", binding.repo.slug)
                        put("prPipeline", binding.prPipeline.toString())
                        binding.pushPipeline?.let { put("pushPipeline", it.toString()) }
                        put("trackedBranch", binding.trackedBranch)
                    }
                }
            }
            putJsonArray("cursors") {
                readCursors().forEach { (key, sha) ->
                    addJsonObject {
                        put("key", key)
                        put("sha", sha)
                    }
                }
            }
            // Liveness (036): the last successful poll, its age, whether it has gone stale, and the cycle
            // count. Absent when the poller has never written a heartbeat (liveness unknown).
            HeartbeatState.read(heartbeat)?.let { hb ->
                val ageSeconds = maxOf(0L, (System.currentTimeMillis() - hb.lastPolledMillis) / MILLIS_PER_SECOND)
                putJsonObject("heartbeat") {
                    put("lastPolledMillis", hb.lastPolledMillis)
                    put("ageSeconds", ageSeconds)
                    put("stale", ageSeconds > STALE_FACTOR * parsed.pollIntervalSeconds)
                    put("cycles", hb.cycles)
                }
            }
        }.toString()
        ok(json)
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

    private fun ok(json: String): ResponseEntity<ByteArray> =
        ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.toByteArray())

    private fun error(message: String): ResponseEntity<ByteArray> {
        val body = buildJsonObject { put("error", message) }.toString().toByteArray()
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON).body(body)
    }

    private companion object {
        private const val MILLIS_PER_SECOND = 1000L
        // A heartbeat older than this many poll intervals is "stale" — one missed poll is jitter, several
        // is a problem.
        private const val STALE_FACTOR = 3L
    }
}
