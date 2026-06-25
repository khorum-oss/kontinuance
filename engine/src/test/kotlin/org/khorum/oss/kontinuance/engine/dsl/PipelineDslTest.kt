package org.khorum.oss.kontinuance.engine.dsl

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.RunStep
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class PipelineDslTest {

    @Test
    fun `builders produce the expected model`() {
        val pipeline = pipeline("build-and-test") {
            concurrency = 2
            stage("build") {
                step("compile") {
                    run("./gradlew build")
                    timeout = 5.minutes
                }
            }
            stage("test") {
                step("unit") {
                    run("./gradlew test")
                    secrets("TOKEN")
                    condition = false
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
    fun `a step without a command is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            pipeline("p") {
                stage("s") {
                    step("x") { /* no run(...) */ }
                }
            }
        }
    }

    @Test
    fun `concurrency defaults to 1`() {
        val pipeline = pipeline("p") { stage("s") { } }
        assertEquals(1, pipeline.concurrency)
    }
}
