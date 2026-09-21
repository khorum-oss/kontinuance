package org.khorum.oss.kontinuance.engine.descriptor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Parsing of the step-level `env:` key.
 *
 * Without it, a descriptor's only route to a step's environment was `secrets:` — which redacts the value
 * in the logs. Non-secret configuration (a checkout path, a registry host) therefore printed as `***`
 * exactly when a pipeline was being debugged for the first time.
 */
class StepEnvDescriptorTest {

    private fun oneStep(vararg stepLines: String): String {
        val step = stepLines.joinToString("\n") { "          $it" }
        return "pipeline:\n  name: \"p\"\n  stages:\n    - name: \"s\"\n      steps:\n        -\n$step"
    }

    private fun stepOf(yaml: String) = PipelineDescriptor.parse(yaml).stages[0].steps[0]

    @Test
    fun `env parses into the step`() {
        val step = stepOf(
            oneStep(
                "name: \"render\"",
                "run: \"true\"",
                "env:",
                "  HUB_DIR: \"/srv/hub\"",
                "  RELIKQARY_DIR: \"/srv/app\"",
            ),
        )

        assertEquals(mapOf("HUB_DIR" to "/srv/hub", "RELIKQARY_DIR" to "/srv/app"), step.env)
    }

    @Test
    fun `a step without env has none`() {
        assertEquals(emptyMap<String, String>(), stepOf(oneStep("name: \"x\"", "run: \"true\"")).env)
    }

    /** `env` and `secrets` coexist — the point is that they are different seams, not alternatives. */
    @Test
    fun `env and secrets can be declared together`() {
        val step = stepOf(
            oneStep(
                "name: \"publish\"",
                "run: \"true\"",
                "secrets: [\"REGISTRY_PW\"]",
                "env:",
                "  REGISTRY_HOST: \"stage.example\"",
            ),
        )

        assertEquals(listOf("REGISTRY_PW"), step.secrets.map { it.name })
        assertEquals(mapOf("REGISTRY_HOST" to "stage.example"), step.env)
    }

    /** The parser is strict elsewhere; `env` must not become the one place a typo passes silently. */
    @Test
    fun `a non-map env is rejected`() {
        val ex = assertFailsWith<DescriptorException> {
            PipelineDescriptor.parse(oneStep("name: \"x\"", "run: \"true\"", "env: \"HUB_DIR=/srv\""))
        }
        assertTrue(ex.message!!.contains("env"), "message should name the offending key: ${ex.message}")
    }
}
