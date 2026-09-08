package org.khorum.oss.kontinuance.engine.descriptor

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.RunStep
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PipelineDescriptorTest {

    // A pipeline must declare at least one stage, so tests whose subject lies elsewhere in the
    // descriptor still need one. Flow style keeps that filler to a single line.
    private val fillerStage = """stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]"""

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
    fun `a descriptor with no stages key is rejected`() {
        // Previously this parsed into a stage-less pipeline that completed Success having run nothing —
        // and with a project source attached, the synthesized checkout made it look like a run that had
        // started and then hung. Failing here makes the omission visible before anything is triggered.
        val yaml = """
            pipeline:
              name: "test"
        """.trimIndent()

        val error = assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }

        assertTrue(error.message!!.contains("stage"), error.message)
    }

    @Test
    fun `a descriptor whose stages list is empty is rejected`() {
        val yaml = """
            pipeline:
              name: "test"
              stages: []
        """.trimIndent()

        val error = assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }

        assertTrue(error.message!!.contains("stage"), error.message)
    }

    @Test
    fun `concurrency defaults to 1 when omitted`() {
        val yaml = """
            pipeline:
              name: "p"
              $fillerStage
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

    @Test
    fun `parses the optional project name`() {
        val yaml = """
            pipeline:
              name: "relikquary-pr"
              project: "relikquary"
              $fillerStage
        """.trimIndent()

        assertEquals("relikquary", PipelineDescriptor.parse(yaml).project)
    }

    @Test
    fun `project is null when the key is absent`() {
        val yaml = """
            pipeline:
              name: "relikquary-pr"
              $fillerStage
        """.trimIndent()

        assertEquals(null, PipelineDescriptor.parse(yaml).project)
    }

    @Test
    fun `still rejects an unknown top-level pipeline key`() {
        val yaml = """
            pipeline:
              name: "relikquary-pr"
              projekt: "relikquary"
              $fillerStage
        """.trimIndent()

        val error = assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
        assertTrue(error.message!!.contains("projekt"))
    }

    @Test
    fun `rejects a blank project name`() {
        val yaml = """
            pipeline:
              name: "relikquary-pr"
              project: "  "
              $fillerStage
        """.trimIndent()

        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
    }

    private fun descriptorWithProject(project: String) = """
        pipeline:
          name: "relikquary-pr"
          project: "$project"
          $fillerStage
    """.trimIndent()

    @Test
    fun `rejects a project name that is not a safe slug`() {
        // Previously this parsed fine and then resolved to NO project at run time, so the pipeline ran
        // normally while its runs quietly grouped under nothing. Failing here makes the typo visible.
        val error = assertFailsWith<DescriptorException> {
            PipelineDescriptor.parse(descriptorWithProject("../escape"))
        }

        assertTrue(error.message!!.contains("project"))
    }

    @Test
    fun `rejects a project name containing a space`() {
        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(descriptorWithProject("my project")) }
    }

    @Test
    fun `rejects a project name longer than 64 characters`() {
        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(descriptorWithProject("a".repeat(65))) }
    }

    @Test
    fun `accepts dots dashes and underscores in a project name`() {
        val parsed = PipelineDescriptor.parse(descriptorWithProject("my.project_name-1"))

        assertEquals("my.project_name-1", parsed.project)
    }
}
