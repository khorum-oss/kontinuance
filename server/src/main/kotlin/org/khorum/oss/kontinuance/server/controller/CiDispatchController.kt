package org.khorum.oss.kontinuance.server.controller

import org.khorum.oss.kontinuance.server.domain.ErrorResponse
import org.khorum.oss.kontinuance.server.domain.ci.CiDispatchRequest
import org.khorum.oss.kontinuance.server.domain.ci.CiDispatchResponse
import org.khorum.oss.kontinuance.server.service.CiDispatcher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * `POST /api/ci/dispatch` — an external CI caller asks Kontinuance to build one commit.
 *
 * `202` started a run. `200` means this commit is already building and carries that run's id, so a caller
 * re-running its job attaches to the build in flight rather than queueing a second one — the distinction
 * is in the status code because a client that cannot tell them apart will double-build. `400` refuses with
 * a caller-facing reason.
 *
 * Authentication is the CI bearer token; the routes it may reach are enumerated in
 * [org.khorum.oss.kontinuance.server.config.CiScope].
 */
@RestController
class CiDispatchController(private val dispatcher: CiDispatcher) {

    @PostMapping("/api/ci/dispatch")
    suspend fun dispatch(@RequestBody request: CiDispatchRequest): ResponseEntity<*> =
        when (val result = dispatcher.dispatch(request)) {
            is CiDispatcher.Result.Accepted ->
                ResponseEntity.status(HttpStatus.ACCEPTED).body(CiDispatchResponse(result.id, "Running"))
            is CiDispatcher.Result.Existing ->
                ResponseEntity.ok(CiDispatchResponse(result.id, "AlreadyRunning"))
            is CiDispatcher.Result.Rejected ->
                ResponseEntity.badRequest().body(ErrorResponse(result.reason))
        }
}
