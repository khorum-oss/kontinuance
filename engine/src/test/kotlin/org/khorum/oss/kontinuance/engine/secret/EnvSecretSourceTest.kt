package org.khorum.oss.kontinuance.engine.secret

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnvSecretSourceTest {

    @Test
    fun `resolves a known secret from the backing environment`() {
        val source = EnvSecretSource(mapOf("TOKEN" to "s3cr3t"))
        assertEquals("s3cr3t", source.resolve("TOKEN"))
    }

    @Test
    fun `returns null for an unknown secret so callers can fail fast`() {
        val source = EnvSecretSource(mapOf("TOKEN" to "s3cr3t"))
        assertNull(source.resolve("MISSING"))
    }
}
