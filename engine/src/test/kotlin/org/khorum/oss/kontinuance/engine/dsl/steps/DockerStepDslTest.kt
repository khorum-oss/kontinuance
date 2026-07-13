package org.khorum.oss.kontinuance.engine.dsl.steps

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.model.DockerStep
import kotlin.test.assertEquals

class DockerStepDslTest {

    private val yaml = """
        pipeline:
          name: "p"
          stages:
            - name: "package"
              steps:
                - name: "image"
                  docker:
                    build:
                      context: "."
                      dockerfile: "Dockerfile"
                      tags: ["myapp:ci"]
                - name: "smoke"
                  docker:
                    run:
                      image: "node:20"
                      command: ["node", "--version"]
    """.trimIndent()

    private val dsl = pipeline {
        name = "p"
        stages {
            stage {
                name = "package"
                steps {
                    dockerStep("image") {
                        build {
                            context = "."
                            dockerfile = "Dockerfile"
                            tags("myapp:ci")
                        }
                    }
                    dockerStep("smoke") {
                        run {
                            image = "node:20"
                            command("node", "--version")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `dockerStep DSL and docker descriptor produce equal models`() {
        assertEquals(PipelineDescriptor.parse(yaml), dsl)
    }

    @Test
    fun `run and build map to the right DockerStep modes`() {
        val steps = dsl.stages[0].steps
        val expectedBuild = DockerStep.build(context = ".", dockerfile = "Dockerfile", tags = listOf("myapp:ci"))
        val expectedRun = DockerStep.run(image = "node:20", command = listOf("node", "--version"))
        assertEquals(expectedBuild, steps[0].definition)
        assertEquals(expectedRun, steps[1].definition)
    }
}
