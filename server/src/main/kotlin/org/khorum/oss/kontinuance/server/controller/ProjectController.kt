package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.server.domain.ErrorResponse
import org.khorum.oss.kontinuance.server.domain.project.ActiveProject
import org.khorum.oss.kontinuance.server.domain.project.CreateProjectRequest
import org.khorum.oss.kontinuance.server.domain.project.CreatedProject
import org.khorum.oss.kontinuance.server.domain.project.ProjectDto
import org.khorum.oss.kontinuance.server.domain.project.ProjectSource
import org.khorum.oss.kontinuance.server.domain.project.ProjectSourceResponse
import org.khorum.oss.kontinuance.server.domain.project.ProjectsResponse
import org.khorum.oss.kontinuance.server.domain.project.SourceRequest
import org.khorum.oss.kontinuance.server.store.ProjectStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path

/**
 * Manages the named pipeline descriptors ("projects", 032) and which one is active:
 *
 * - `GET /api/projects` — list the projects (with each one's optional source, 033) and the active one,
 *   seeding a `default` project from the current descriptor on first use.
 * - `POST /api/projects` — register `{name, text, repo?, branch?}`; the name must be a safe slug and the
 *   text must parse with the engine's strict parser, or it is rejected `400` (and `409` if the name exists).
 * - `POST /api/projects/{name}/activate` — make a project active: write its descriptor to the server's live
 *   descriptor file (so the trigger and Config screen use it) and record it as active; `404` if unknown.
 * - `POST /api/projects/{name}/source` — set/update a project's source (repo/branch, 033); `404` if unknown.
 *
 * Handlers return typed DTOs the Jackson codec serializes.
 */
@RestController
class ProjectController(
    private val store: ProjectStore,
    @Value($$"${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    @GetMapping("/api/projects")
    suspend fun list(): ProjectsResponse = withContext(Dispatchers.IO) {
        seedIfEmpty()
        val active = store.activeName()
        ProjectsResponse(
            active = active,
            projects = store.list().map { name ->
                val src = store.source(name)
                ProjectDto(name = name, active = name == active, repo = src?.repo, branch = src?.branch)
            },
        )
    }

    @PostMapping("/api/projects")
    suspend fun create(@RequestBody(required = false) request: CreateProjectRequest?): ResponseEntity<*> {
        val name = request?.name
        val text = request?.text
        if (name == null || text == null) {
            return badRequest("malformed request body — expected {\"name\": …, \"text\": …}")
        }
        if (!ProjectStore.isValidName(name)) {
            return badRequest("invalid project name (use letters, digits, and . _ -)")
        }
        if (store.exists(name)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("project already exists: $name"))
        }
        runCatching { PipelineDescriptor.parse(text) }
            .getOrElse { return badRequest(it.message ?: "invalid descriptor") }
        withContext(Dispatchers.IO) {
            store.save(name, text)
            // Persist the optional source (033) alongside the descriptor when a repo was supplied.
            store.saveSource(name, ProjectSource(request.repo, request.branch))
        }
        return ResponseEntity.ok(CreatedProject(name))
    }

    @PostMapping("/api/projects/{name}/activate")
    suspend fun activate(@PathVariable name: String): ResponseEntity<*> {
        val text = withContext(Dispatchers.IO) { store.get(name) }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("no such project: $name"))
        withContext(Dispatchers.IO) {
            descriptor.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            Files.writeString(descriptor, text)
            store.setActive(name)
        }
        return ResponseEntity.ok(ActiveProject(name))
    }

    @PostMapping("/api/projects/{name}/source")
    suspend fun setSource(
        @PathVariable name: String,
        @RequestBody(required = false) request: SourceRequest?,
    ): ResponseEntity<*> {
        if (!store.exists(name)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("no such project: $name"))
        }
        val source = ProjectSource(request?.repo, request?.branch)
        withContext(Dispatchers.IO) { store.saveSource(name, source) }
        return ResponseEntity.ok(
            if (source.hasRepo) {
                ProjectSourceResponse(name, source.repo, source.branch?.takeIf { it.isNotBlank() })
            } else {
                ProjectSourceResponse(name)
            },
        )
    }

    /** On first use, register the current descriptor file as `default` and activate it. */
    private fun seedIfEmpty() {
        if (store.list().isNotEmpty()) return
        if (!Files.isRegularFile(descriptor)) return
        val text = Files.readString(descriptor)
        runCatching { PipelineDescriptor.parse(text) }.getOrNull() ?: return
        store.save(DEFAULT, text)
        store.setActive(DEFAULT)
    }

    private fun badRequest(message: String): ResponseEntity<*> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(message))

    private companion object {
        const val DEFAULT = "default"
    }
}