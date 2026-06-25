package org.khorum.oss.kontinuance.engine.dsl

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.RunStep
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class PipelineDslTest {

    @Test
    fun `the generated builders produce the expected model`() {
        val pipeline = pipeline {
            name = "build-and-test"
            concurrency = 2
            stages {
                stage {
                    name = "build"
                    steps {
                        step {
                            name = "compile"
                            run("./gradlew build")
                            timeout = 5.minutes
                        }
                    }
                }
                stage {
                    name = "test"
                    steps {
                        step {
                            name = "unit"
                            run("./gradlew test")
                            secrets("TOKEN")
                            condition(false)
                        }
                    }
                }
            }
        }

        assertEquals("build-and-test", pipeline.name)
        assertEquals(2, pipeline.concurrency)

        val compile = pipeline.stages[0].steps[0]
        assertEquals(RunStep("./gradlew build"), compile.definition)
        assertEquals(5.minutes, compile.timeout)

        val unit = pipeline.stages[1].steps[0]
        assertEquals(listOf("TOKEN"), unit.secrets.map { it.name })
        assertEquals(false, unit.condition)
    }

    @Test
    fun `a step without a command is rejected at build`() {
        assertFailsWith<IllegalArgumentException> {
            pipeline {
                name = "p"
                stages {
                    stage {
                        name = "s"
                        steps {
                            step { name = "x" } // no run(...)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `concurrency defaults to 1 and collections default to empty`() {
        val pipeline = pipeline { name = "p" }
        assertEquals(1, pipeline.concurrency)
        assertEquals(emptyList(), pipeline.stages)
    }
}
