package org.khorum.oss.kontinuance.engine.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GradleStepTest {

    @Test
    fun `defaults are empty args and the wrapper enabled`() {
        val step = GradleStep(tasks = listOf("build"))
        assertEquals(listOf("build"), step.tasks)
        assertEquals(emptyList(), step.args)
        assertTrue(step.useWrapper)
    }

    @Test
    fun `at least one task is required`() {
        assertFailsWith<IllegalArgumentException> { GradleStep(tasks = emptyList()) }
    }
}
