package org.khorum.oss.kontinuance.engine.cli

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class RunnerTest {

    private fun descriptorWith(dir: Path, command: String): String {
        val file = dir.resolve("pipeline.yaml")
        Files.writeString(
            file,
            """
            pipeline:
              name: "cli-test"
              stages:
                - name: "s"
                  steps:
                    - name: "step"
                      run: "$command"
            """.trimIndent(),
        )
        return file.toString()
    }

    @Test
    fun `returns 0 when the pipeline succeeds`(@TempDir dir: Path) {
        assertEquals(0, Runner.run(arrayOf(descriptorWith(dir, "true"))))
    }

    @Test
    fun `returns 1 when a step fails`(@TempDir dir: Path) {
        assertEquals(1, Runner.run(arrayOf(descriptorWith(dir, "false"))))
    }

    @Test
    fun `returns 2 when no descriptor path is given`() {
        assertEquals(2, Runner.run(emptyArray()))
    }

    @Test
    fun `returns 2 when the descriptor cannot be read`() {
        assertEquals(2, Runner.run(arrayOf("/no/such/descriptor.yaml")))
    }

    @Test
    fun `returns 2 when the descriptor is malformed`(@TempDir dir: Path) {
        val bad = dir.resolve("bad.yaml")
        Files.writeString(bad, "not: a valid pipeline descriptor")
        assertEquals(2, Runner.run(arrayOf(bad.toString())))
    }

    @Test
    fun `check parses without executing (would-fail pipeline still returns 0)`(@TempDir dir: Path) {
        // The step command exits non-zero; --check must NOT run it, so the result is 0, not 1.
        assertEquals(0, Runner.run(arrayOf("--check", descriptorWith(dir, "false"))))
    }

    @Test
    fun `check returns 2 on a malformed descriptor`(@TempDir dir: Path) {
        val bad = dir.resolve("bad.yaml")
        Files.writeString(bad, "not: a valid pipeline descriptor")
        assertEquals(2, Runner.run(arrayOf("--check", bad.toString())))
    }
}
