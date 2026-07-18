package org.khorum.oss.kontinuance.engine.dsl.steps

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.model.GitStep
import kotlin.test.assertEquals

class GitStepDslTest {

    @Test
    fun `gitStep builds the same model as the git descriptor key`() {
        val fromDsl = pipeline {
            name = "p"
            stages {
                stage {
                    name = "s"
                    steps {
                        gitStep("checkout") {
                            url = "https://example.com/repo.git"
                            ref = "main"
                            dir = "src"
                        }
                    }
                }
            }
        }

        val fromYaml = PipelineDescriptor.parse(
            """
            pipeline:
              name: "p"
              stages:
                - name: "s"
                  steps:
                    - name: "checkout"
                      git: { url: "https://example.com/repo.git", ref: "main", dir: "src" }
            """.trimIndent(),
        )

        val expected = GitStep(url = "https://example.com/repo.git", ref = "main", dir = "src")
        assertEquals(expected, fromDsl.stages[0].steps[0].definition)
        assertEquals(fromYaml.stages[0].steps[0].definition, fromDsl.stages[0].steps[0].definition)
    }
}
