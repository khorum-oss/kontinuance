package org.khorum.oss.kontinuance.engine.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NpmStepTest {

    @Test
    fun `script requires a non-empty script`() {
        assertFailsWith<IllegalArgumentException> { NpmStep.script("") }

        val step = NpmStep.script("test")
        assertEquals(NpmMode.SCRIPT, step.mode)
        assertEquals("test", step.script)
    }

    @Test
    fun `install toggles clean between ci and install`() {
        assertTrue(NpmStep.install().clean)
        assertTrue(NpmStep.install(clean = true).clean)
        assertFalse(NpmStep.install(clean = false).clean)
    }
}
