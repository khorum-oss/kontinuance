package org.khorum.oss.kontinuance.server.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DescriptorConfigReaderTest {

    private val descriptor = """
        pipeline:
          name: "demo"
          concurrency: 2
          stages:
            - name: "build"
              steps:
                - name: "assemble"
                  run: "echo build"
            - name: "publish"
              steps:
                - name: "push"
                  run: "echo publish"
    """.trimIndent()

    @Test
    fun `reads the real descriptor text and derives the plan`(@TempDir dir: Path) {
        val file = dir.resolve("kontinuance.yml")
        Files.writeString(file, descriptor)

        val config = DescriptorConfigReader.read(file)
        requireNotNull(config)

        assertEquals("kontinuance.yml", config.source)
        assertTrue(config.text.contains("name: \"demo\""), "returns the real yaml text")
        assertEquals(2, config.plan.stages)
        assertEquals(2, config.plan.tasks)
        assertEquals(2, config.plan.maxParallel)
        assertEquals("run", config.plan.toolchain)
        assertEquals("publish", config.plan.publish)
        assertEquals("—", config.plan.deploy)
    }

    @Test
    fun `still returns the text with a zeroed plan when the descriptor does not parse`(@TempDir dir: Path) {
        val file = dir.resolve("bad.yml")
        Files.writeString(file, "not: a: valid: pipeline")
        val config = DescriptorConfigReader.read(file)
        requireNotNull(config)
        assertTrue(config.text.contains("not: a: valid: pipeline"))
        assertEquals(0, config.plan.stages)
    }

    @Test
    fun `returns null when the descriptor is absent`(@TempDir dir: Path) {
        assertNull(DescriptorConfigReader.read(dir.resolve("missing.yml")))
    }
}
