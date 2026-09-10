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
import org.khorum.oss.kontinuance.server.domain.project.Rejected
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
 * Serves and edits `/api/config`. `GET` asks [DescriptorResolver] what would actually run (041, FR-008)
 * and renders the response from exactly that — its own text and its own parsed pipeline — so `source`,
 * `text`, and `origin` can never disagree. When resolution fails for the *active project* it reports
 * `origin: "unresolved"` and the resolver's reason instead of a descriptor; only with no active project
 * does it fall back to reading a real Kontinuance descriptor off local disk (parsed by
 * [DescriptorConfigReader]), or fixture data if even that is absent. `PUT` (027) accepts an edited
 * descriptor `{ "text": … }`, validates it with the engine's strict parser via [DescriptorConfigWriter],
 * and — only if it parses — writes it to the descriptor file *and* to the active project's stored slot
 * (so an edit against a repo-hosted project becomes a visible override rather than a one-off change to
 * the live file), returning the refreshed projection; an invalid edit is rejected `400` with the
 * parser's message and never overwrites anything. `DELETE
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
    suspend fun config(): ConfigResponse = when (val resolution = resolver.resolve()) {
        is Resolved -> withContext(Dispatchers.IO) { renderResolved(resolution) }
        is Rejected -> withContext(Dispatchers.IO) { rejected(resolution) }
    }

    /**
     * What to show when nothing resolved.
     *
     * With an active project, the answer is the resolver's reason and no descriptor at all. Falling back
     * to the live descriptor file here would show whichever project was activated last — or, on a fresh
     * server, fabricated fixture content — under this project's name, and an EDIT + SAVE would then store
     * that unrelated pipeline as this project's override. It would also contradict `RunTrigger`, which
     * refuses with exactly this reason.
     *
     * With no active project there is no project whose resolution could be said to have failed: the live
     * file is legitimately what a plain single-descriptor deployment runs (FR-009a), so that path keeps
     * its pre-041 behaviour, fixture content included.
     */
    private fun rejected(rejection: Rejected): ConfigResponse {
        val active = projects.activeName()
            ?: return DescriptorConfigReader.read(descriptor) ?: StubFixtures.config()
        return DescriptorConfigReader.project(source = active, text = "", pipeline = null)
            .copy(origin = "unresolved", reason = rejection.reason)
    }

    /**
     * Renders exactly what [resolved] holds — its [Resolved.text] and [Resolved.pipeline] — through the
     * same plan-summary logic [DescriptorConfigReader.read] uses, so the projection can never show one
     * descriptor's content under another descriptor's label (the defect this exists to prevent: a
     * repo-hosted project's Config screen previously showed the local live-descriptor file's content
     * mislabelled as `origin: "repo"`). [source] names where that text came from, matching [origin] so
     * the two never contradict: the project name for a stored descriptor, `owner/repo@branch` for a
     * repo-hosted one, or the live descriptor's filename when no project is active.
     */
    private fun renderResolved(resolved: Resolved): ConfigResponse {
        val active = projects.activeName()
        val projectSource = active?.let { projects.source(it) }
        val repo = projectSource?.repo?.let { RepoRef.parse(it) }
        val source = when (resolved.origin) {
            Origin.Live -> descriptor.fileName.toString()
            Origin.Stored -> active ?: "stored"
            Origin.Repo -> repo?.let { "${it.slug}@${projectSource.branch ?: "?"}" } ?: "repository"
        }
        // Only a stored descriptor can be "overriding" anything, and only when a repository is actually
        // there to be shadowed — a stored descriptor on a project with no source, or one whose source
        // isn't GitHub, is just that project's config, not an override (041, FR-008).
        val overridden = resolved.origin == Origin.Stored && repo != null
        return DescriptorConfigReader.project(source, resolved.text, resolved.pipeline)
            .copy(origin = resolved.origin.name.lowercase(), overridden = overridden)
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
