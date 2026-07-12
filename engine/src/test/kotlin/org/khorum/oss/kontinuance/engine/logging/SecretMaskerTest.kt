package org.khorum.oss.kontinuance.engine.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretMaskerTest {

    @Test
    fun `a registered secret never appears unmasked`() {
        val masker = SecretMasker(listOf("s3cr3t"))
        val masked = masker.mask("connecting with token s3cr3t now")
        assertFalse(masked.contains("s3cr3t"), "secret leaked: $masked")
        assertTrue(masked.contains("***"))
    }

    @Test
    fun `masks every occurrence on a line`() {
        val masker = SecretMasker(listOf("abc"))
        assertEquals("*** then *** again", masker.mask("abc then abc again"))
    }

    @Test
    fun `longer secrets are masked before shorter overlapping ones`() {
        // "abcdef" must be fully redacted even though "abc" is also a registered secret.
        val masker = SecretMasker(listOf("abc", "abcdef"))
        val masked = masker.mask("value=abcdef")
        assertFalse(masked.contains("def"), "partial leak: $masked")
    }

    @Test
    fun `blank secret values are ignored`() {
        val masker = SecretMasker(listOf("", "   "))
        assertTrue(masker.isEmpty)
        assertEquals("unchanged line", masker.mask("unchanged line"))
    }
}
