package org.khorum.oss.kontinuance.engine.descriptor

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.dsl.steps.TypedStepOptions
import org.khorum.oss.kontinuance.engine.dsl.steps.gradleStep
import org.khorum.oss.kontinuance.engine.model.PullPolicy
import org.khorum.oss.kontinuance.engine.model.RunnerOptions
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RunnerOptionsDescriptorTest {

    @Test
    fun `a runner block parses into the step's RunnerOptions`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "build"
                  steps:
                    - name: "assemble"
                      image: "gradle:8.8-jdk21"
                      runner:
                        network: "none"
                        pull: "missing"
                        mapUser: true
                      gradle:
                        tasks: ["assemble"]
        """.trimIndent()

        val step = PipelineDescriptor.parse(yaml).stages[0].steps[0]

        assertEquals(RunnerOptions(network = "none", pull = PullPolicy.MISSING, mapUser = true), step.runner)
    }

    @Test
    fun `a step with no runner block has a null runner`() {
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

        assertNull(PipelineDescriptor.parse(yaml).stages[0].steps[0].runner)
    }

    @Test
    fun `the descriptor runner and the DSL runner produce equal models`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "build"
                  steps:
                    - name: "assemble"
                      image: "gradle:8.8-jdk21"
                      runner:
                        network: "none"
                        pull: "always"
                      gradle:
                        tasks: ["assemble"]
        """.trimIndent()

        val dsl = pipeline {
            name = "p"
            stages {
                stage {
                    name = "build"
                    steps {
                        gradleStep(
                            "assemble",
                            TypedStepOptions(
                                image = "gradle:8.8-jdk21",
                                runner = RunnerOptions(network = "none", pull = PullPolicy.ALWAYS),
                            ),
                        ) { tasks("assemble") }
                    }
                }
            }
        }

        assertEquals(PipelineDescriptor.parse(yaml), dsl)
    }

    @Test
    fun `an unknown pull policy is rejected`() {
        val yaml = runnerYaml("pull: \"sometimes\"")
        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
    }

    @Test
    fun `an unknown runner key is rejected by the strict parser`() {
        val yaml = runnerYaml("bogus: true")
        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
    }

    @Test
    fun `runner options without an image are rejected`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "build"
                  steps:
                    - name: "assemble"
                      runner:
                        network: "none"
                      gradle:
                        tasks: ["assemble"]
        """.trimIndent()

        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
    }

    private fun runnerYaml(runnerLine: String): String = """
        pipeline:
          name: "p"
          stages:
            - name: "build"
              steps:
                - name: "assemble"
                  image: "gradle:8.8-jdk21"
                  runner:
                    $runnerLine
                  gradle:
                    tasks: ["assemble"]
    """.trimIndent()
}
