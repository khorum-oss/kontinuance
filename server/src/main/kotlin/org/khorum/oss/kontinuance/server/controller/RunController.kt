package org.khorum.oss.kontinuance.server.controller

import org.khorum.oss.kontinuance.server.domain.ErrorResponse
import org.khorum.oss.kontinuance.server.domain.RunsResponse
import org.khorum.oss.kontinuance.server.domain.StatusMessage
import org.khorum.oss.kontinuance.server.service.RunReadFacade
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Coroutine (`suspend`) WebFlux handlers serving the 007 `/api` contract from the Spring Boot runtime:
 * health, list runs (newest-first, bounded), and run-by-id (404 when absent). Each handler suspends over
 * [RunReadFacade] (which offloads the blocking store), so request handling is non-blocking (FR-003), and
 * returns a typed value the framework's Jackson codec serializes to `application/json`. Unknown paths
 * (404) and unsupported methods (405) are handled by the framework's default routing.
 */
@RestController
class RunController(private val facade: RunReadFacade) {

    @GetMapping("/api/health")
    suspend fun health(): StatusMessage = facade.health()

    @GetMapping("/api/runs")
    suspend fun listRuns(@RequestParam(required = false) limit: Int?): RunsResponse = facade.listRuns(limit)

    @GetMapping("/api/runs/{id}")
    suspend fun getRun(@PathVariable id: String): ResponseEntity<*> =
        facade.getRun(id)?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("not found"))
}