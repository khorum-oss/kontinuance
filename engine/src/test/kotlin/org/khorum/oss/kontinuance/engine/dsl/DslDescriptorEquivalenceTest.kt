package org.khorum.oss.kontinuance.engine.dsl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class DslDescriptorEquivalenceTest {

    private val yaml = """
        pipeline:
          name: "build-and-test"
          concurrency: 2
          stages:
            - name: "build"
              steps:
                - name: "compile"
                  run: "true"
                  timeout: "5m"
            - name: "test"
              steps:
                - name: "unit"
                  run: "echo done"
    """.trimIndent()

    private val dsl = pipeline {
        name = "build-and-test"
        concurrency = 2
        stages {
            stage {
                name = "build"
                steps {
                    step {
                        name = "compile"
                        run("true")
                        timeout = 5.minutes
                    }
                }
            }
            stage {
                name = "test"
                steps {
                    step {
                        name = "unit"
                        run("echo done")
                    }
                }
            }
        }
    }

    @Test
    fun `identical YAML and DSL definitions produce equal models`() {
        assertEquals(PipelineDescriptor.parse(yaml), dsl)
    }

    @Test
    fun `equal models run with identical ordering and final status`() = runBlocking {
        val fromYaml = PipelineEngine.default(CapturingLogSink()).run(PipelineDescriptor.parse(yaml))
        val fromDsl = PipelineEngine.default(CapturingLogSink()).run(dsl)

        assertEquals(fromYaml.status, fromDsl.status)
        assertEquals(
            fromYaml.stageRuns.map { it.name to it.stepRuns.map { step -> step.name } },
            fromDsl.stageRuns.map { it.name to it.stepRuns.map { step -> step.name } },
        )
    }
}
