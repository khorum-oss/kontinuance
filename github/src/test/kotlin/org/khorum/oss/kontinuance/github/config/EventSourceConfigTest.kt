package org.khorum.oss.kontinuance.github.config

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EventSourceConfigTest {

    @Test
    fun `parses repositories, interval, and token env`() {
        val config = EventSourceConfig.parse(
            """
            eventSource:
              tokenEnv: "GH_TOKEN"
              pollIntervalSeconds: 15
              repositories:
                - owner: "khorum-oss"
                  name: "relikquary"
                  prPipeline: "pipelines/pr.yaml"
                  pushPipeline: "pipelines/deliver.yaml"
                  trackedBranch: "release"
            """.trimIndent(),
        )

        assertEquals("GH_TOKEN", config.tokenEnv)
        assertEquals(15, config.pollIntervalSeconds)
        assertEquals("https://api.github.com", config.baseUrl)
        val binding = config.bindings.single()
        assertEquals(RepoRef("khorum-oss", "relikquary"), binding.repo)
        assertEquals(Path.of("pipelines/pr.yaml"), binding.prPipeline)
        assertEquals(Path.of("pipelines/deliver.yaml"), binding.pushPipeline)
        assertEquals("release", binding.trackedBranch)
    }

    @Test
    fun `renders back to YAML that parses to the same config`() {
        val original = EventSourceConfig(
            tokenEnv = "GH_TOKEN",
            pollIntervalSeconds = 45,
            baseUrl = "https://github.example.com/api/v3",
            bindings = listOf(
                RepositoryBinding(
                    repo = RepoRef("acme", "widgets"),
                    prPipeline = Path.of("/etc/kontinuance/pipelines/pr.yaml"),
                    pushPipeline = Path.of("/etc/kontinuance/pipelines/deliver.yaml"),
                    trackedBranch = "release",
                ),
                RepositoryBinding(
                    repo = RepoRef("acme", "gadgets"),
                    prPipeline = Path.of("/etc/kontinuance/pipelines/gadgets-pr.yaml"),
                ),
            ),
        )

        assertEquals(original, EventSourceConfig.parse(original.render()))
    }

    @Test
    fun `renders a name containing a quote without breaking the document`() {
        val original = EventSourceConfig(
            tokenEnv = "GH_TOKEN",
            pollIntervalSeconds = 60,
            baseUrl = "https://api.github.com",
            bindings = listOf(
                RepositoryBinding(
                    repo = RepoRef("acme", "it's-a-repo"),
                    prPipeline = Path.of("/pipelines/o'brien.yaml"),
                ),
            ),
        )

        val reparsed = EventSourceConfig.parse(original.render())
        assertEquals("it's-a-repo", reparsed.bindings.single().repo.name)
        assertEquals(Path.of("/pipelines/o'brien.yaml"), reparsed.bindings.single().prPipeline)
    }

    @Test
    fun `applies defaults for interval, base url, tracked branch, and optional push pipeline`() {
        val config = EventSourceConfig.parse(
            """
            eventSource:
              tokenEnv: "GITHUB_TOKEN"
              repositories:
                - owner: "o"
                  name: "r"
                  prPipeline: "pr.yaml"
            """.trimIndent(),
        )

        assertEquals(60, config.pollIntervalSeconds)
        assertEquals("https://api.github.com", config.baseUrl)
        val binding = config.bindings.single()
        assertEquals("main", binding.trackedBranch)
        assertNull(binding.pushPipeline)
    }

    @Test
    fun `rejects config with no repositories`() {
        assertFailsWith<IllegalArgumentException> {
            EventSourceConfig.parse("eventSource:\n  tokenEnv: \"T\"\n  repositories: []")
        }
    }

    @Test
    fun `rejects a repository missing a required field`() {
        assertFailsWith<IllegalStateException> {
            EventSourceConfig.parse(
                """
                eventSource:
                  tokenEnv: "T"
                  repositories:
                    - owner: "o"
                      prPipeline: "pr.yaml"
                """.trimIndent(),
            )
        }
    }
}
