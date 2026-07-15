package org.khorum.oss.kontinuance.server

/**
 * A transport-agnostic API result: an HTTP status and a JSON body. [RunApi] returns these and
 * [HttpApiServer] writes them to the wire — so the same read logic can back a different transport
 * (e.g. a Spring Boot / SSE layer) later without change (FR-008 / SC-007).
 */
data class ApiResponse(val status: Int, val json: String)
