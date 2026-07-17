package org.khorum.oss.kontinuance.engine.descriptor

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.ApprovalStep
import org.khorum.oss.kontinuance.engine.model.DockerStep
import org.khorum.oss.kontinuance.engine.model.GradleStep
import org.khorum.oss.kontinuance.engine.model.NpmStep
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TypedStepDescriptorTest {

    /** Wraps a single step's YAML (its keys, one per line) in a minimal one-stage pipeline. */
    private fun oneStep(vararg stepLines: String): String {
        val step = stepLines.joinToString("\n") { "          $it" }
        return "pipeline:\n  name: \"p\"\n  stages:\n    - name: \"s\"\n      steps:\n        -\n$step"
    }

    private fun definitionOf(yaml: String) =
        PipelineDescriptor.parse(yaml).stages[0].steps[0].definition

    @Test
    fun `a step declaring no definition is rejected`() {
        val ex = assertFailsWith<DescriptorException> { PipelineDescriptor.parse(oneStep("name: \"x\"")) }
        assertTrue(ex.message!!.contains("exactly one"), "message: ${ex.message}")
    }

    @Test
    fun `a step declaring two definitions is rejected`() {
        val yaml = oneStep("name: \"x\"", "run: \"echo hi\"", "gradle: { tasks: [\"build\"] }")
        val ex = assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
        assertTrue(ex.message!!.contains("exactly one"), "message: ${ex.message}")
    }

    @Test
    fun `gradle, docker, and npm keys map to their typed models`() {
        val gradle = oneStep("name: \"x\"", "gradle: { tasks: [\"build\"], args: [\"-x\", \"test\"] }")
        val docker = oneStep("name: \"x\"", "docker: { run: { image: \"node:20\", command: [\"node\"] } }")
        val npm = oneStep("name: \"x\"", "npm: { install: { clean: true } }")

        assertEquals(GradleStep(tasks = listOf("build"), args = listOf("-x", "test")), definitionOf(gradle))
        assertEquals(DockerStep.run(image = "node:20", command = listOf("node")), definitionOf(docker))
        assertEquals(NpmStep.install(clean = true), definitionOf(npm))
    }

    @Test
    fun `the approval key maps to an ApprovalStep carrying its message`() {
        val yaml = oneStep("name: \"promote\"", "approval: \"Promote to production?\"")
        assertEquals(ApprovalStep("Promote to production?"), definitionOf(yaml))
    }

    @Test
    fun `docker declaring both run and build is rejected`() {
        val yaml = oneStep("name: \"x\"", "docker: { run: { image: \"a\", command: [\"b\"] }, build: {} }")
        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
    }

    @Test
    fun `npm declaring both script and install is rejected`() {
        val yaml = oneStep("name: \"x\"", "npm: { script: \"test\", install: { clean: true } }")
        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
    }
}
