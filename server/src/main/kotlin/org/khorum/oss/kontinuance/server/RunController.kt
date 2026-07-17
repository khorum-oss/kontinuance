package org.khorum.oss.kontinuance.server

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Coroutine (`suspend`) WebFlux handlers serving the unchanged 007 `/api` contract from the Spring Boot
 * runtime: health, list runs (newest-first, bounded), and run-by-id (404 when absent). Each handler
 * suspends over [RunReadFacade] (which offloads the blocking store), so request handling is non-blocking
 * (FR-003). The facade returns the transport-agnostic [ApiResponse]; [toEntity] writes its raw JSON body
 * straight to the wire with the given status, keeping the byte shape byte-for-byte identical to 007
 * (SC-001) rather than round-tripping through the framework's JSON mapper. Unknown paths (404) and
 * unsupported methods (405) are handled by the framework's default routing.
 */
@RestController
class RunController(private val facade: RunReadFacade) {

    @GetMapping("/api/health")
    suspend fun health(): ResponseEntity<ByteArray> = facade.health().toEntity()

    @GetMapping("/api/runs")
    suspend fun listRuns(@RequestParam(required = false) limit: Int?): ResponseEntity<ByteArray> =
        facade.listRuns(limit).toEntity()

    @GetMapping("/api/runs/{id}")
    suspend fun getRun(@PathVariable id: String): ResponseEntity<ByteArray> = facade.getRun(id).toEntity()

    // Write the pre-serialized JSON as raw bytes: WebFlux has no String->application/json writer (the
    // CharSequence encoder only emits text/plain), and bytes preserve the exact 007 byte shape (SC-001).
    private fun ApiResponse.toEntity(): ResponseEntity<ByteArray> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.toByteArray())
}
