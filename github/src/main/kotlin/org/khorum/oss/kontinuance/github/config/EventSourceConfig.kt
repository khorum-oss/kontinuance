package org.khorum.oss.kontinuance.github.config

import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Native configuration for the event-source service. Authored in Kontinuance's own schema — **not** a
 * GitHub Actions workflow and not derived from any GitHub YAML. Relative pipeline paths resolve against
 * the config file's directory. Example:
 *
 * ```yaml
 * eventSource:
 *   tokenEnv: "GITHUB_TOKEN"              # env var holding the PAT (never inline the token)
 *   baseUrl: "https://api.github.com"     # optional; set for GitHub Enterprise
 *   pollIntervalSeconds: 30               # optional; default 60
 *   repositories:
 *     - owner: "khorum-oss"
 *       name: "relikquary"
 *       prPipeline: "pipelines/pr.yaml"          # run for PRs (the gating check)
 *       pushPipeline: "pipelines/deliver.yaml"   # optional; run for a push to trackedBranch
 *       trackedBranch: "main"                    # optional; default "main"
 * ```
 */
data class EventSourceConfig(
    val tokenEnv: String,
    val pollIntervalSeconds: Long,
    val baseUrl: String,
    val bindings: List<RepositoryBinding>,
) {
    companion object {
        private const val DEFAULT_POLL_SECONDS = 60L
        private const val DEFAULT_BASE_URL = "https://api.github.com"

        /** Loads and validates the config from [path]; relative pipeline paths resolve against its dir. */
        fun load(path: Path): EventSourceConfig = parse(path.readText(), path.toAbsolutePath().parent)

        /** Parses config from [yaml]; [baseDir] (if given) is where relative pipeline paths resolve. */
        fun parse(yaml: String, baseDir: Path? = null): EventSourceConfig {
            val root = asMap(Load(LoadSettings.builder().build()).loadFromString(yaml), "document")
            val source = asMap(root["eventSource"], "eventSource")
            val repos = (source["repositories"] as? List<*>).orEmpty()
            require(repos.isNotEmpty()) { "eventSource.repositories must list at least one repository" }
            return EventSourceConfig(
                tokenEnv = (source["tokenEnv"] as? String)?.takeIf { it.isNotBlank() }
                    ?: error("eventSource.tokenEnv is required"),
                pollIntervalSeconds = (source["pollIntervalSeconds"] as? Number)?.toLong() ?: DEFAULT_POLL_SECONDS,
                baseUrl = (source["baseUrl"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
                bindings = repos.mapIndexed { i, raw -> binding(asMap(raw, "repositories[$i]"), baseDir) },
            )
        }

        private fun binding(map: Map<String, Any?>, baseDir: Path?): RepositoryBinding {
            val owner = required(map, "owner")
            val name = required(map, "name")
            return RepositoryBinding(
                repo = RepoRef(owner, name),
                prPipeline = resolve(required(map, "prPipeline"), baseDir),
                pushPipeline = (map["pushPipeline"] as? String)?.takeIf { it.isNotBlank() }?.let { resolve(it, baseDir) },
                trackedBranch = (map["trackedBranch"] as? String)?.takeIf { it.isNotBlank() } ?: "main",
            )
        }

        private fun resolve(value: String, baseDir: Path?): Path {
            val path = Path.of(value)
            return if (path.isAbsolute || baseDir == null) path else baseDir.resolve(path)
        }

        private fun required(map: Map<String, Any?>, key: String): String =
            (map[key] as? String)?.takeIf { it.isNotBlank() } ?: error("repository config is missing '$key'")

        @Suppress("UNCHECKED_CAST")
        private fun asMap(value: Any?, where: String): Map<String, Any?> =
            value as? Map<String, Any?> ?: error("expected a mapping at '$where'")
    }
}
