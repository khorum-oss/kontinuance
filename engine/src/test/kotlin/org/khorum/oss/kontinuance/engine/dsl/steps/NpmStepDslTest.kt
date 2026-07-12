package org.khorum.oss.kontinuance.engine.dsl.steps

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.model.NpmStep
import kotlin.test.assertEquals

class NpmStepDslTest {

    private val yaml = """
        pipeline:
          name: "p"
          stages:
            - name: "web"
              steps:
                - name: "web-tests"
                  npm:
                    script: "test"
                - name: "deps"
                  npm:
                    install:
                      clean: true
    """.trimIndent()

    private val dsl = pipeline {
        name = "p"
        stages {
            stage {
                name = "web"
                steps {
                    npmStep("web-tests") { script("test") }
                    npmStep("deps") { installClean() }
                }
            }
        }
    }

    @Test
    fun `npmStep DSL and npm descriptor produce equal models`() {
        assertEquals(PipelineDescriptor.parse(yaml), dsl)
    }

    @Test
    fun `script and install map to the right NpmStep modes`() {
        val steps = dsl.stages[0].steps
        assertEquals(NpmStep.script("test"), steps[0].definition)
        assertEquals(NpmStep.install(clean = true), steps[1].definition)
    }
}
