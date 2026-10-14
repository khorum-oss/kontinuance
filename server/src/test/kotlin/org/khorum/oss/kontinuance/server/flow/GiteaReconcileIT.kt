package org.khorum.oss.kontinuance.server.flow

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.FileRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.server.service.GitFixture
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * The start-up reconcile over the real runtime (043): the server starts against a run history and a
 * stand-in Gitea, and delivers — and publishes — exactly the heads no successful delivery has reached.
 *
 * The history is seeded with the file backend before the context starts, which is the state a restarted
 * server finds on disk.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiteaReconcileIT {

    private fun runsAt(sha: String): List<RunRecord> = FileRunStore(runs).recent(100).filter { it.sha == sha }

    private fun awaitRun(sha: String): RunRecord {
        val deadline = System.currentTimeMillis() + 30_000
        while (runsAt(sha).none { it.trigger == "gitea-reconcile" } && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return runsAt(sha).firstOrNull { it.trigger == "gitea-reconcile" } ?: error("no reconcile run at $sha: ${runsAt(sha)}")
    }

    @Test
    fun `a head no delivery has reached is delivered and published, and reports as kontinuance-deliver`() {
        val run = awaitRun(BEHIND_HEAD)
        assert(run.pipeline == "deliver" && run.repo == BEHIND) { "$run" }

        val deadline = System.currentTimeMillis() + 30_000
        while (GitFixture.refs(BEHIND_REMOTE)["refs/heads/main"] != BEHIND_HEAD && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assert(GitFixture.refs(BEHIND_REMOTE) == mapOf("refs/heads/main" to BEHIND_HEAD)) { "${GitFixture.refs(BEHIND_REMOTE)}" }

        while (statuses.none { it.first == BEHIND_HEAD && it.second.contains("\"kontinuance/deliver\"") } &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50)
        }
        assert(statuses.any { it.first == BEHIND_HEAD && it.second.contains("\"kontinuance/deliver\"") }) { "$statuses" }
    }

    @Test
    fun `a head whose delivery failed is delivered again`() {
        awaitRun(FAILED_HEAD)
    }

    @Test
    fun `a head already delivered successfully starts nothing`() {
        // The other heads' runs are started in the same pass; once they exist, this one would have too.
        awaitRun(FAILED_HEAD)
        awaitRun(BEHIND_HEAD)

        assert(runsAt(CURRENT_HEAD).map { it.id } == listOf("run-seeded-current")) { "${runsAt(CURRENT_HEAD)}" }
    }

    private companion object {
        const val GONE = "khorum-oss/gone"
        const val BEHIND = "khorum-oss/behind"
        const val CURRENT = "khorum-oss/current"
        const val FAILED = "khorum-oss/failed"
        val CURRENT_HEAD = "c".repeat(40)
        val FAILED_HEAD = "d".repeat(40)

        private val dir: Path = Files.createTempDirectory("gitea-reconcile-it")
        private val runs: Path = dir.resolve("runs")
        private val sources: Path = dir.resolve("sources")
        val BEHIND_REMOTE: Path = GitFixture.remote(dir.resolve("behind-remote.git"))
        val BEHIND_HEAD: String = GitFixture.source(sources, "khorum-oss", "behind").let { GitFixture.git(it, "rev-parse", "main") }

        val statuses = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>()

        /** Stands in for Gitea. [GONE] is listed first and answers 404, so the pass must survive it. */
        private val gitea: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            mapOf(BEHIND to BEHIND_HEAD, CURRENT to CURRENT_HEAD, FAILED to FAILED_HEAD).forEach { (repo, head) ->
                createContext("/api/v1/repos/$repo/statuses/") { exchange ->
                    statuses += exchange.requestURI.path.substringAfterLast('/') to String(exchange.requestBody.readAllBytes())
                    exchange.sendResponseHeaders(201, -1)
                    exchange.close()
                }
                createContext("/api/v1/repos/$repo/branches/main") { exchange ->
                    val bytes = """{"name":"main","commit":{"id":"$head"}}""".toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
            start()
        }

        private val config: Path = dir.resolve("ci.yaml").also {
            listOf("pr", "deliver").forEach { name ->
                Files.writeString(
                    dir.resolve("$name.yaml"),
                    "pipeline:\n  name: $name\n  stages:\n    - name: noop\n      steps:\n        - name: x\n          run: \"true\"\n",
                )
            }
            fun repo(name: String, extra: String = "") =
                "    - {owner: khorum-oss, name: $name, prPipeline: pr.yaml, pushPipeline: deliver.yaml$extra}\n"
            Files.writeString(
                it,
                "eventSource:\n  tokenEnv: GITHUB_TOKEN\n  cloneBase: https://gitea.example\n  repositories:\n" +
                    repo("gone") +
                    repo("behind", ", cloneBase: '$sources', publish: {remote: '$BEHIND_REMOTE'}") +
                    repo("current") +
                    repo("failed"),
            )
            val store = FileRunStore(runs)
            fun seed(id: String, repo: String, sha: String, status: String) = store.record(
                RunRecord(
                    id = id, pipeline = "deliver", status = status, startedAt = Instant.EPOCH,
                    repo = repo, sha = sha, trigger = "github-actions",
                ),
            )
            seed("run-seeded-behind", BEHIND, "1".repeat(40), "Success")
            seed("run-seeded-current", CURRENT, CURRENT_HEAD, "Success")
            seed("run-seeded-failed", FAILED, FAILED_HEAD, "Failed")
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("kontinuance.ci.config") { config.toString() }
            registry.add("kontinuance.store") { runs.toString() }
            registry.add("kontinuance.store.backend") { "file" }
            registry.add("kontinuance.hooks.gitea.api-url") { "http://127.0.0.1:${gitea.address.port}" }
            registry.add("kontinuance.publish.work-dir") { dir.resolve("publish").toString() }
            registry.add("kontinuance.github.autostart") { "false" }
        }
    }
}
