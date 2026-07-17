package org.khorum.oss.kontinuance.server.config

import org.khorum.oss.kontinuance.server.stub.StubFixtures
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Path

/**
 * Serves `/api/config` from a real Kontinuance descriptor when present (parsed by
 * [DescriptorConfigReader]), falling back to fixture data otherwise. The descriptor path comes from
 * `kontinuance.config.descriptor` (default `kontinuance.yml`, relative to the server's working
 * directory). Additive and dependency-free — the engine's own parser does the work.
 */
@RestController
class ConfigController(
    @param:Value("\${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    @GetMapping("/api/config")
    fun config(): ResponseEntity<ByteArray> {
        val body = DescriptorConfigReader.read(descriptor) ?: StubFixtures.config()
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body.toByteArray())
    }
}
