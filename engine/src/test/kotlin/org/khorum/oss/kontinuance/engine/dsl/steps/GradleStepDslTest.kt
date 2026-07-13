package org.khorum.oss.kontinuance.engine.dsl.steps

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.model.GradleStep
import kotlin.test.assertEquals

class GradleStepDslTest {

    private val yaml = """
        pipeline:
          name: "p"
          stages:
            - name: "build"
              steps:
                - name: "compile"
                  gradle:
                    tasks: ["build"]
                    args: ["-x", "test"]
    """.trimIndent()

    private val dsl = pipeline {
        name = "p"
        stages {
            stage {
                name = "build"
                steps {
                    gradleStep("compile") {
                        tasks("build")
                        args("-x", "test")
                    }
                }
            }
        }
    }

    @Test
    fun `gradleStep DSL and gradle descriptor produce equal models`() {
        assertEquals(PipelineDescriptor.parse(yaml), dsl)
    }

    @Test
    fun `the built definition is a GradleStep`() {
        val expected = GradleStep(tasks = listOf("build"), args = listOf("-x", "test"))
        assertEquals(expected, dsl.stages[0].steps[0].definition)
    }
}
