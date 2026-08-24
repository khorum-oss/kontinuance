package org.khorum.oss.kontinuance.server.domain.project

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.RunRecord
import kotlin.test.assertEquals

class ProjectResolverTest {

    private fun record(project: String? = null, repo: String? = null) =
        RunRecord(id = "r", pipeline = "p", status = "Success", repo = repo, project = project)

    @Test
    fun `prefers the explicit project`() {
        assertEquals("relikquary", ProjectResolver.resolve(record(project = "relikquary", repo = "khorum-oss/other")))
    }

    @Test
    fun `falls back to the repository short name`() {
        assertEquals("relikquary", ProjectResolver.resolve(record(repo = "khorum-oss/relikquary")))
    }

    @Test
    fun `handles a repository with no owner segment`() {
        assertEquals("relikquary", ProjectResolver.resolve(record(repo = "relikquary")))
    }

    @Test
    fun `resolves to none when there is neither project nor repo`() {
        assertEquals(null, ProjectResolver.resolve(record()))
    }

    @Test
    fun `treats a blank project as absent and uses the repo`() {
        assertEquals("relikquary", ProjectResolver.resolve(record(project = "   ", repo = "khorum-oss/relikquary")))
    }

    @Test
    fun `resolves to none when the name is not a safe slug`() {
        assertEquals(null, ProjectResolver.resolve(record(repo = "khorum-oss/reli kquary")))
        assertEquals(null, ProjectResolver.resolve(record(project = "../escape")))
    }

    @Test
    fun `resolves to none for a trailing-slash repo`() {
        assertEquals(null, ProjectResolver.resolve(record(repo = "khorum-oss/")))
    }

    @Test
    fun `an invalid explicit project resolves to none and does not fall back to the repo`() {
        assertEquals(null, ProjectResolver.resolve(record(project = "../escape", repo = "khorum-oss/relikquary")))
    }
}
