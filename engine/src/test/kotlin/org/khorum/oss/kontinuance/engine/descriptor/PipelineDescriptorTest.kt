package org.khorum.oss.kontinuance.engine.descriptor

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.RunStep
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PipelineDescriptorTest {

    @Test
    fun `parses a valid descriptor into the model`() {
        val yaml = """
            pipeline:
              name: "build-and-test"
              concurrency: 2
              stages:
                - name: "build"
                  steps:
                    - name: "compile"
                      run: "./gradlew build"
                      timeout: "5m"
                      secrets: ["TOKEN"]
                - name: "test"
                  steps:
                    - name: "unit"
                      run: "./gradlew test"
                      when: false
        """.trimIndent()

        val pipeline = PipelineDescriptor.parse(yaml)

        assertEquals("build-and-test", pipeline.name)
        assertEquals(2, pipeline.concurrency)
        assertEquals(listOf("build", "test"), pipeline.stages.map { it.name })

        val compile = pipeline.stages[0].steps[0]
        assertEquals("compile", compile.name)
        assertEquals(RunStep("./gradlew build"), compile.definition)
        assertEquals(5.minutes, compile.timeout)
        assertEquals(listOf("TOKEN"), compile.secrets.map { it.name })

        val unit = pipeline.stages[1].steps[0]
        assertEquals(false, unit.condition)
    }

    @Test
    fun `concurrency defaults to 1 when omitted`() {
        val yaml = """
            pipeline:
              name: "p"
              stages: []
        """.trimIndent()
        assertEquals(1, PipelineDescriptor.parse(yaml).concurrency)
    }

    @Test
    fun `an unknown key is rejected with its location`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "s"
                  steps:
                    - name: "x"
                      run: "true"
                      bogus: 1
        """.trimIndent()
        val ex = assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
        assertTrue(ex.message!!.contains("bogus"), ex.message)
        assertTrue(ex.message!!.contains("steps[0]"), ex.message)
    }

    @Test
    fun `a missing required run key is rejected`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "s"
                  steps:
                    - name: "x"
        """.trimIndent()
        val ex = assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
        assertTrue(ex.message!!.contains("run"), ex.message)
    }

    @Test
    fun `a malformed timeout is rejected`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "s"
                  steps:
                    - name: "x"
                      run: "true"
                      timeout: "soon"
        """.trimIndent()
        val ex = assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
        assertTrue(ex.message!!.contains("duration"), ex.message)
    }

    @Test
    fun `duplicate stage names are rejected`() {
        val yaml = """
            pipeline:
              name: "p"
              stages:
                - name: "dup"
                  steps: []
                - name: "dup"
                  steps: []
        """.trimIndent()
        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
    }
}
