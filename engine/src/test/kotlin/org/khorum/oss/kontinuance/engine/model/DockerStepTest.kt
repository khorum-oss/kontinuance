package org.khorum.oss.kontinuance.engine.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DockerStepTest {

    @Test
    fun `run requires a non-empty image and command`() {
        assertFailsWith<IllegalArgumentException> { DockerStep.run(image = "", command = listOf("x")) }
        assertFailsWith<IllegalArgumentException> { DockerStep.run(image = "node:20", command = emptyList()) }

        val step = DockerStep.run(image = "node:20", command = listOf("node", "--version"))
        assertEquals(DockerMode.RUN, step.mode)
        assertEquals("node:20", step.image)
    }

    @Test
    fun `build requires a non-empty context and defaults it to dot`() {
        assertFailsWith<IllegalArgumentException> { DockerStep.build(context = "") }

        val step = DockerStep.build()
        assertEquals(DockerMode.BUILD, step.mode)
        assertEquals(".", step.context)
    }
}
