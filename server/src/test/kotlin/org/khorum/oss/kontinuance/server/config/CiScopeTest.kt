package org.khorum.oss.kontinuance.server.config

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CiScopeTest {

    @Test
    fun `allows exactly the dispatch, read and cancel routes a CI caller needs`() {
        assertTrue(CiScope.allows("/api/ci/dispatch", HttpMethod.POST))
        assertTrue(CiScope.allows("/api/runs/run-1", HttpMethod.GET))
        assertTrue(CiScope.allows("/api/runs/run-1/logs", HttpMethod.GET))
        assertTrue(CiScope.allows("/api/runs/run-1/logs/stream", HttpMethod.GET))
        assertTrue(CiScope.allows("/api/runs/run-1/cancel", HttpMethod.POST))
    }

    @Test
    fun `refuses configuration, projects and source`() {
        assertFalse(CiScope.allows("/api/config", HttpMethod.PUT))
        assertFalse(CiScope.allows("/api/config", HttpMethod.GET))
        assertFalse(CiScope.allows("/api/projects", HttpMethod.POST))
        assertFalse(CiScope.allows("/api/source", HttpMethod.POST))
    }

    @Test
    fun `refuses the whole-server run feeds a CI caller has no business reading`() {
        assertFalse(CiScope.allows("/api/runs", HttpMethod.GET), "the runs list is everyone's runs")
        assertFalse(CiScope.allows("/api/runs/stream", HttpMethod.GET), "the global SSE feed is not one run")
    }

    @Test
    fun `refuses triggering the active project, which is not a dispatch`() {
        assertFalse(CiScope.allows("/api/runs/trigger", HttpMethod.POST))
    }

    @Test
    fun `refuses the right path with the wrong method`() {
        assertFalse(CiScope.allows("/api/ci/dispatch", HttpMethod.GET))
        assertFalse(CiScope.allows("/api/runs/run-1", HttpMethod.DELETE))
        assertFalse(CiScope.allows("/api/runs/run-1/cancel", HttpMethod.GET))
    }

    @Test
    fun `refuses approve and reject, which are an operator's decision`() {
        assertFalse(CiScope.allows("/api/runs/run-1/approve", HttpMethod.POST))
        assertFalse(CiScope.allows("/api/runs/run-1/reject", HttpMethod.POST))
    }
}
