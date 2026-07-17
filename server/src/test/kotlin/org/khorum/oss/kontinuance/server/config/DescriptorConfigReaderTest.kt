package org.khorum.oss.kontinuance.server.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
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

        val json = DescriptorConfigReader.read(file)
        requireNotNull(json)

        assertTrue(json.contains("\"source\":\"kontinuance.yml\""))
        assertTrue(json.contains("pipeline:") && json.contains("name: \\\"demo\\\""), "returns the real yaml text")
        assertTrue(json.contains("\"stages\":2") && json.contains("\"tasks\":2"))
        assertTrue(json.contains("\"maxParallel\":2"))
        assertTrue(json.contains("\"toolchain\":\"run\""))
        assertTrue(json.contains("\"publish\":\"publish\"") && json.contains("\"deploy\":\"—\""))
    }

    @Test
    fun `still returns the text with a zeroed plan when the descriptor does not parse`(@TempDir dir: Path) {
        val file = dir.resolve("bad.yml")
        Files.writeString(file, "not: a: valid: pipeline")
        val json = DescriptorConfigReader.read(file)
        requireNotNull(json)
        assertTrue(json.contains("not: a: valid: pipeline"))
        assertTrue(json.contains("\"stages\":0"))
    }

    @Test
    fun `returns null when the descriptor is absent`(@TempDir dir: Path) {
        assertNull(DescriptorConfigReader.read(dir.resolve("missing.yml")))
    }
}
