package org.khorum.oss.kontinuance.server.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.khorum.oss.kontinuance.server.stub.StubFixtures
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Path

/**
 * Serves and edits `/api/config`. `GET` reads a real Kontinuance descriptor when present (parsed by
 * [DescriptorConfigReader]), falling back to fixture data otherwise. `PUT` (027) accepts an edited
 * descriptor `{ "text": … }`, validates it with the engine's strict parser via [DescriptorConfigWriter],
 * and — only if it parses — writes it to the descriptor file and returns the refreshed projection; an
 * invalid edit is rejected `400` with the parser's message and never overwrites the file on disk. The
 * descriptor path comes from `kontinuance.config.descriptor` (default `kontinuance.yml`, relative to the
 * server's working directory). Additive and dependency-free.
 */
@RestController
class ConfigController(
    @Value("\${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    @GetMapping("/api/config")
    fun config(): ResponseEntity<ByteArray> {
        val body = DescriptorConfigReader.read(descriptor) ?: StubFixtures.config()
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body.toByteArray())
    }

    @PutMapping("/api/config")
    suspend fun update(@RequestBody(required = false) requestBody: String?): ResponseEntity<ByteArray> {
        val text = parseText(requestBody)
            ?: return error(HttpStatus.BAD_REQUEST, "malformed request body — expected {\"text\": …}")
        return when (val result = withContext(Dispatchers.IO) { DescriptorConfigWriter.write(descriptor, text) }) {
            is DescriptorConfigWriter.Result.Written ->
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result.json.toByteArray())
            is DescriptorConfigWriter.Result.Invalid ->
                error(HttpStatus.BAD_REQUEST, result.message)
        }
    }

    // Extract `text` from a `{ "text": … }` JSON body with kotlinx-serialization (no jackson dependency);
    // a missing field or non-JSON body yields null → a 400 from the caller.
    private fun parseText(requestBody: String?): String? {
        if (requestBody.isNullOrBlank()) return null
        return runCatching {
            Json.parseToJsonElement(requestBody).jsonObject["text"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    private fun error(status: HttpStatus, message: String): ResponseEntity<ByteArray> {
        val body = buildJsonObject { put("error", message) }.toString().toByteArray()
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body)
    }
}
