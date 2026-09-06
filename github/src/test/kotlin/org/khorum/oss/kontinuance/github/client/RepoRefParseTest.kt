package org.khorum.oss.kontinuance.github.client

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepoRefParseTest {

    @Test
    fun `parses the https form a stored source holds`() {
        assertEquals(
            RepoRef("khorum-oss", "spektr"),
            RepoRef.parse("https://github.com/khorum-oss/spektr"),
        )
    }

    @Test
    fun `tolerates a git suffix and a trailing slash`() {
        val expected = RepoRef("khorum-oss", "spektr")
        assertEquals(expected, RepoRef.parse("https://github.com/khorum-oss/spektr.git"))
        assertEquals(expected, RepoRef.parse("https://github.com/khorum-oss/spektr/"))
        assertEquals(expected, RepoRef.parse("https://github.com/khorum-oss/spektr.git/"))
    }

    @Test
    fun `parses the ssh form`() {
        assertEquals(
            RepoRef("khorum-oss", "spektr"),
            RepoRef.parse("git@github.com:khorum-oss/spektr.git"),
        )
    }

    @Test
    fun `returns null for a repository that is not on GitHub`() {
        assertNull(RepoRef.parse("https://gitlab.com/khorum-oss/spektr"))
        assertNull(RepoRef.parse("https://example.test/khorum-oss/spektr"))
    }

    @Test
    fun `returns null for a url with no owner and name`() {
        assertNull(RepoRef.parse("https://github.com/khorum-oss"))
        assertNull(RepoRef.parse("not a url"))
        assertNull(RepoRef.parse(""))
    }
}
