package org.khorum.oss.kontinuance.server.controller.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.server.domain.ErrorResponse
import org.khorum.oss.kontinuance.server.domain.ConfigResponse
import org.khorum.oss.kontinuance.server.domain.ConfigUpdateRequest
import org.khorum.oss.kontinuance.server.domain.StubFixtures
import org.khorum.oss.kontinuance.server.domain.project.DescriptorResolver
import org.khorum.oss.kontinuance.server.domain.project.Origin
import org.khorum.oss.kontinuance.server.domain.project.Resolved
import org.khorum.oss.kontinuance.server.store.ProjectStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Path

/**
 * Serves and edits `/api/config`. `GET` reads a real Kontinuance descriptor when present (parsed by
 * [DescriptorConfigReader]), falling back to fixture data otherwise, and annotates the projection with
 * `origin`/`overridden` from [DescriptorResolver] (041, FR-008) — the resolver, not this file, knows
 * whether the active project's real descriptor actually lives in its repository rather than on this
 * server's disk. `PUT` (027) accepts an edited descriptor `{ "text": … }`, validates it with the engine's
 * strict parser via [DescriptorConfigWriter], and — only if it parses — writes it to the descriptor file
 * *and* to the active project's stored slot (so an edit against a repo-hosted project becomes a visible
 * override rather than a one-off change to the live file), returning the refreshed projection; an invalid
 * edit is rejected `400` with the parser's message and never overwrites anything. `DELETE
 * /api/config/override` deletes the active project's stored descriptor, reverting it to its repository;
 * it is `409` when there is nothing to revert. The descriptor path comes from
 * `kontinuance.config.descriptor` (default `kontinuance.yml`, relative to the server's working directory).
 */
@RestController
class ConfigController(
    private val projects: ProjectStore,
    @Value("\${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
    private val resolver: DescriptorResolver,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    @GetMapping("/api/config")
    suspend fun config(): ConfigResponse {
        val base = withContext(Dispatchers.IO) { DescriptorConfigReader.read(descriptor) } ?: StubFixtures.config()
        val origin = (resolver.resolve() as? Resolved)?.origin ?: Origin.Live
        val overridden = origin == Origin.Stored && withContext(Dispatchers.IO) { overridesRepository() }
        return base.copy(origin = origin.name.lowercase(), overridden = overridden)
    }

    /**
     * True only when the active project's stored descriptor is shadowing a repository that also has one —
     * a stored descriptor on a project with no source (or a source pointing somewhere other than GitHub)
     * isn't overriding anything, so it must not raise the override banner.
     */
    private fun overridesRepository(): Boolean {
        val active = projects.activeName() ?: return false
        val repo = projects.source(active)?.repo ?: return false
        return RepoRef.parse(repo) != null
    }

    @PutMapping("/api/config")
    suspend fun update(@RequestBody(required = false) request: ConfigUpdateRequest?): ResponseEntity<*> {
        val text = request?.text
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse("malformed request body — expected {\"text\": …}"))
        return when (val result = withContext(Dispatchers.IO) { DescriptorConfigWriter.write(descriptor, text) }) {
            is DescriptorConfigWriter.Result.Written -> {
                // Keep the active project's snapshot in sync with the edit (032) — for a repo-hosted
                // project this is exactly what turns the edit into a visible override (041, FR-008), so
                // switching projects and back preserves it rather than reverting to the repository's copy.
                withContext(Dispatchers.IO) { projects.activeName()?.let { projects.save(it, text) } }
                ResponseEntity.ok(config())
            }
            is DescriptorConfigWriter.Result.Invalid ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(result.message))
        }
    }

    @DeleteMapping("/api/config/override")
    suspend fun revert(): ResponseEntity<*> = withContext(Dispatchers.IO) {
        val active = projects.activeName()
            ?: return@withContext conflict("no active project")
        if (projects.get(active) == null) {
            return@withContext conflict("this project is not overriding a repository descriptor")
        }
        if (projects.source(active) == null) {
            return@withContext conflict("this project has no repository to revert to")
        }
        projects.delete(active)
        ResponseEntity.ok(config())
    }

    private fun conflict(message: String): ResponseEntity<*> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(message))
}
