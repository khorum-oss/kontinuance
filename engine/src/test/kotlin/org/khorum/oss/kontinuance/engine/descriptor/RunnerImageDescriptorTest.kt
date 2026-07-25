package org.khorum.oss.kontinuance.engine.descriptor

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.dsl.run
import org.khorum.oss.kontinuance.engine.dsl.steps.TypedStepOptions
import org.khorum.oss.kontinuance.engine.dsl.steps.gradleStep
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RunnerImageDescriptorTest {

    @Test
    fun `a step-level image key parses into the step's runner image`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "build"
                  steps:
                    - name: "assemble"
                      image: "gradle:8.8-jdk21"
                      gradle:
                        tasks: ["assemble"]
        """.trimIndent()

        val step = PipelineDescriptor.parse(yaml).stages[0].steps[0]

        assertEquals("gradle:8.8-jdk21", step.image)
    }

    @Test
    fun `a step with no image key has a null image (host execution)`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "build"
                  steps:
                    - name: "assemble"
                      gradle:
                        tasks: ["assemble"]
        """.trimIndent()

        assertNull(PipelineDescriptor.parse(yaml).stages[0].steps[0].image)
    }

    @Test
    fun `the descriptor image and the DSL image produce equal models`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "build"
                  steps:
                    - name: "assemble"
                      image: "gradle:8.8-jdk21"
                      gradle:
                        tasks: ["assemble"]
        """.trimIndent()

        val dsl = pipeline {
            name = "p"
            stages {
                stage {
                    name = "build"
                    steps {
                        gradleStep("assemble", TypedStepOptions(image = "gradle:8.8-jdk21")) {
                            tasks("assemble")
                        }
                    }
                }
            }
        }

        assertEquals(PipelineDescriptor.parse(yaml), dsl)
    }

    @Test
    fun `the v0 step block also exposes image on the generated builder`() {
        val dsl = pipeline {
            name = "p"
            stages {
                stage {
                    name = "build"
                    steps {
                        step {
                            name = "assemble"
                            image = "gradle:8.8-jdk21"
                            run("./gradlew assemble")
                        }
                    }
                }
            }
        }

        assertEquals("gradle:8.8-jdk21", dsl.stages[0].steps[0].image)
    }

    @Test
    fun `a blank image is rejected at model construction`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "build"
                  steps:
                    - name: "assemble"
                      image: "  "
                      gradle:
                        tasks: ["assemble"]
        """.trimIndent()

        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
    }
}
