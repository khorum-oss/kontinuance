package org.khorum.oss.kontinuance.engine

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.dsl.run
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.assertEquals

/**
 * Exercises the exact flow documented in `specs/001-pipeline-foundation/quickstart.md`
 * so the documented entry points cannot drift from the implementation.
 */
class QuickstartTest {

    private val yaml = """
        pipeline:
          name: "build-and-test"
          concurrency: 2
          stages:
            - name: "build"
              steps:
                - name: "compile"
                  run: "echo compiling && true"
            - name: "test"
              steps:
                - name: "unit"
                  run: "echo testing && true"
    """.trimIndent()

    @Test
    fun `run a pipeline loaded from a YAML file`() = runBlocking {
        val file = Files.createTempFile("build-and-test", ".yaml")
        try {
            file.writeText(yaml)
            val pipeline = PipelineDescriptor.load(file)
            val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)
            assertEquals(PipelineStatus.Success, run.status)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `the DSL equivalent produces the identical final status`() = runBlocking {
        val pipeline = pipeline {
            name = "build-and-test"
            concurrency = 2
            stages {
                stage {
                    name = "build"
                    steps { step { name = "compile"; run("echo compiling && true") } }
                }
                stage {
                    name = "test"
                    steps { step { name = "unit"; run("echo testing && true") } }
                }
            }
        }
        val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)
        assertEquals(PipelineStatus.Success, run.status)
    }
}
