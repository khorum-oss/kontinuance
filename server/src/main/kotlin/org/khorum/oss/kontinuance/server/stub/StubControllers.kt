package org.khorum.oss.kontinuance.server.stub

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Additive STUB endpoints backing the forward-looking Web UI screens (pipeline, deploy, coverage, config).
 * Each serves the fixture JSON from [StubFixtures] as raw bytes (same byte-exact approach as
 * [org.khorum.oss.kontinuance.server.RunController]). They do not touch the existing `/api` read contract
 * and add no new dependency, so the supply-chain gate is unaffected. A real data source can replace each
 * body later without changing the route or shape (see specs/009-web-ui/contracts/stub-api.md).
 */
private fun json(body: String): ResponseEntity<ByteArray> =
    ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body.toByteArray())

@RestController
class PipelineStubController {
    @GetMapping("/api/runs/{id}/pipeline")
    fun pipeline(@PathVariable id: String): ResponseEntity<ByteArray> = json(StubFixtures.pipeline(id))
}

@RestController
class DeployStubController {
    @GetMapping("/api/deploy")
    fun deploy(): ResponseEntity<ByteArray> = json(StubFixtures.deploy())
}

@RestController
class ConfigStubController {
    @GetMapping("/api/config")
    fun config(): ResponseEntity<ByteArray> = json(StubFixtures.config())
}
