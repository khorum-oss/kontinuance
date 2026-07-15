package org.khorum.oss.kontinuance.github.config

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.client.RepoRef
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
