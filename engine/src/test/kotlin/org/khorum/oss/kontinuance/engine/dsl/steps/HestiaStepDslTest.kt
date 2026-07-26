package org.khorum.oss.kontinuance.engine.dsl.steps

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.descriptor.DescriptorException
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.model.HestiaStep
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HestiaStepDslTest {

    private val yaml = """
        pipeline:
          name: "p"
          stages:
            - name: "delivery"
              steps:
                - name: "render"
                  render:
                    args: ["--env", "stage"]
                - name: "deploy"
                  deploy:
                    args: ["--env", "prod"]
                  secrets: ["DEPLOY_TOKEN"]
                - name: "uat"
                  uat:
                    args: []
    """.trimIndent()

    private val dsl = pipeline {
        name = "p"
        stages {
            stage {
                name = "delivery"
                steps {
                    renderStep("render", "--env", "stage")
                    deployStep("deploy", "--env", "prod", options = TypedStepOptions(secrets = listOf("DEPLOY_TOKEN")))
                    uatStep("uat")
                }
            }
        }
    }

    @Test
    fun `the render deploy uat DSL and descriptor produce equal models`() {
        assertEquals(PipelineDescriptor.parse(yaml), dsl)
    }

    @Test
    fun `each key maps to the right khorum tool`() {
        val steps = dsl.stages[0].steps
        assertEquals(HestiaStep.render(listOf("--env", "stage")), steps[0].definition)
        assertEquals(HestiaStep.deploy(listOf("--env", "prod")), steps[1].definition)
        assertEquals(HestiaStep.uat(), steps[2].definition)
    }

    @Test
    fun `an unknown key inside a delivery step is rejected by the strict parser`() {
        val bad = """
            pipeline:
              name: "p"
              stages:
                - name: "delivery"
                  steps:
                    - name: "render"
                      render:
                        args: ["x"]
                        bogus: true
        """.trimIndent()

        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(bad) }
    }
}
