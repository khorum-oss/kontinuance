package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.server.domain.RunApi
import org.khorum.oss.kontinuance.server.domain.RunsResponse
import org.khorum.oss.kontinuance.server.domain.StatusMessage
import org.springframework.stereotype.Component

/**
 * The suspending boundary between the coroutine
 * [org.khorum.oss.kontinuance.server.controller.RunController] and the blocking [RunApi] (which reads a
 * file-backed [org.khorum.oss.kontinuance.persistence.RunStore]). Each read is offloaded with
 * `withContext(Dispatchers.IO)` so the WebFlux event-loop thread is never blocked (FR-003 / SC-003),
 * while the read logic itself stays in [RunApi] with no duplication (FR-004 / SC-006). A non-blocking
 * store backend can later replace the offload behind this same suspend surface.
 */
@Component
class RunReadFacade(private val api: RunApi) {

    suspend fun health(): StatusMessage = withContext(Dispatchers.IO) { api.health() }

    suspend fun listRuns(limit: Int?): RunsResponse = withContext(Dispatchers.IO) { api.listRuns(limit) }

    suspend fun getRun(id: String): RunRecord? = withContext(Dispatchers.IO) { api.getRun(id) }
}
