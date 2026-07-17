package org.khorum.oss.kontinuance.server

/**
 * A transport-agnostic API result: an HTTP status and a JSON body. [RunApi] returns these and the
 * [RunController] writes them to the wire via [RunReadFacade] — so the same read logic backs the
 * transport without change (FR-004 / SC-006), and a future SSE layer can reuse it too.
 */
data class ApiResponse(val status: Int, val json: String)
