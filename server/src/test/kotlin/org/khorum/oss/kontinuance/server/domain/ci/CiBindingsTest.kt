package org.khorum.oss.kontinuance.server.domain.ci

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CiBindingsTest {

    private val yaml = """
        eventSource:
          tokenEnv: "GITHUB_TOKEN"
          repositories:
            - owner: "khorum-oss"
              name: "relikquary"
              prPipeline: "relikquary-pr.yaml"
    """.trimIndent()

    private fun configIn(dir: Path): Path =
        dir.resolve("ci.yaml").also { Files.writeString(it, yaml) }

    @Test
    fun `loads the configured bindings`(@TempDir dir: Path) {
        val bindings = CiBindings(configIn(dir)).load().getOrThrow()
        assertEquals("khorum-oss/relikquary", bindings.single().repo.slug)
    }

    @Test
    fun `resolves a relative prPipeline against the config's own directory`(@TempDir dir: Path) {
        val binding = CiBindings(configIn(dir)).load().getOrThrow().single()
        assertEquals(dir.resolve("relikquary-pr.yaml"), binding.prPipeline)
    }

    @Test
    fun `a missing config reports why rather than looking like an empty allow-list`(@TempDir dir: Path) {
        val failure = CiBindings(dir.resolve("absent.yaml")).load().exceptionOrNull()
        assertTrue(failure?.message?.contains("no dispatch config") == true, "got: ${failure?.message}")
    }

    @Test
    fun `an unparseable config reports why rather than looking like an empty allow-list`(@TempDir dir: Path) {
        val config = dir.resolve("ci.yaml").also { Files.writeString(it, "eventSource: {}") }
        assertTrue(CiBindings(config).load().isFailure)
    }
}
