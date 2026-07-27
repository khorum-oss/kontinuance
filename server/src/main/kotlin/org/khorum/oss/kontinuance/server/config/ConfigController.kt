package org.khorum.oss.kontinuance.server.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.server.ErrorResponse
import org.khorum.oss.kontinuance.server.projects.ProjectStore
import org.khorum.oss.kontinuance.server.stub.StubFixtures
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
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
 * server's working directory).
 */
@RestController
class ConfigController(
    private val projects: ProjectStore,
    @param:Value("\${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    @GetMapping("/api/config")
    fun config(): ConfigResponse = DescriptorConfigReader.read(descriptor) ?: StubFixtures.config()

    @PutMapping("/api/config")
    suspend fun update(@RequestBody(required = false) request: ConfigUpdateRequest?): ResponseEntity<*> {
        val text = request?.text
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse("malformed request body — expected {\"text\": …}"))
        return when (val result = withContext(Dispatchers.IO) { DescriptorConfigWriter.write(descriptor, text) }) {
            is DescriptorConfigWriter.Result.Written -> {
                // Keep the active project's snapshot in sync with the edit (032), so switching projects and
                // back preserves it rather than reverting to the stored copy.
                withContext(Dispatchers.IO) { projects.activeName()?.let { projects.save(it, text) } }
                ResponseEntity.ok(result.config)
            }
            is DescriptorConfigWriter.Result.Invalid ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(result.message))
        }
    }
}
