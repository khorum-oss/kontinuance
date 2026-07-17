package org.khorum.oss.kontinuance.server.stub

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The remaining additive STUB endpoint. Deploy state comes from an external system (ArgoCD/registry) not
 * modeled here, so it stays fixture-backed; coverage, config, and pipeline are now served from real
 * sources (Kover report / descriptor / persisted run stages). Serves fixture JSON as raw bytes, adds no
 * dependency, and does not touch the existing `/api` read contract (see contracts/stub-api.md).
 */
private fun json(body: String): ResponseEntity<ByteArray> =
    ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body.toByteArray())

@RestController
class DeployStubController {
    @GetMapping("/api/deploy")
    fun deploy(): ResponseEntity<ByteArray> = json(StubFixtures.deploy())
}

