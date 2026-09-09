package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.domain.ErrorResponse
import org.khorum.oss.kontinuance.server.domain.RunApi
import org.khorum.oss.kontinuance.server.domain.project.ActiveProject
import org.khorum.oss.kontinuance.server.domain.project.CreateProjectRequest
import org.khorum.oss.kontinuance.server.domain.project.CreatedProject
import org.khorum.oss.kontinuance.server.domain.project.DescriptorCheck
import org.khorum.oss.kontinuance.server.domain.project.GitHubClientProvider
import org.khorum.oss.kontinuance.server.domain.project.ProjectDto
import org.khorum.oss.kontinuance.server.domain.project.ProjectResolver
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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Manages the named pipeline descriptors ("projects", 032) and which one is active:
 *
 * - `GET /api/projects` — list the projects (with each one's optional source, 033) and the active one,
 *   seeding a `default` project from the current descriptor on first use.
 * - `POST /api/projects` — register `{name, text?, repo?, branch?}`; the name must be a safe slug, a
 *   supplied text must parse with the engine's strict parser, and a request with neither text nor a repo
 *   is rejected — `400` on either failure (and `409` if the name exists). A repo without text is checked
 *   against GitHub advisorily (041, FR-007): the check result comes back on the response, but a failed
 *   check never blocks creation.
 * - `POST /api/projects/{name}/activate` — make a project active: write its descriptor to the server's live
 *   descriptor file (so the trigger and Config screen use it) and record it as active; `404` if unknown.
 * - `POST /api/projects/{name}/source` — set/update a project's source (repo/branch, 033); `404` if unknown.
 *
 * Handlers return typed DTOs the Jackson codec serializes.
 */
@RestController
class ProjectController(
    private val store: ProjectStore,
    private val runs: RunStore,
    @Value("\${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
    @Value("\${kontinuance.projects.derive-limit:500}") private val deriveLimit: Int,
    private val clients: GitHubClientProvider,
    @Value("\${kontinuance.project.descriptorPath:kontinuance.yml}") private val descriptorFileName: String,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    /**
     * The window statistics are actually derived over, clamped to what `/api/runs` will serve. Without the
     * clamp a wider `derive-limit` would count runs no client could ever load, so the picker's counts and
     * the runs list would disagree with no way for the operator to reconcile them. Reported on the wire so
     * a client can load exactly this many runs.
     */
    private val runWindow: Int = deriveLimit.coerceAtMost(RunApi.MAX_LIMIT)

    @GetMapping("/api/projects")
    suspend fun list(): ProjectsResponse = withContext(Dispatchers.IO) {
        seedIfEmpty()
        val stats = deriveStats()
        val registered = store.list()
        val names = (registered + stats.keys).distinct().sorted()
        val active = store.activeName()?.takeIf { it in names }
        ProjectsResponse(
            active = active,
            projects = names.map { name ->
                val src = store.source(name)
                val stat = stats[name]
                ProjectDto(
                    name = name,
                    active = name == active,
                    repo = src?.repo,
                    branch = src?.branch,
                    derived = name !in registered,
                    // Runnable when there is something to run: a stored descriptor, or a source to read
                    // one from (041). A derived project with neither stays non-runnable.
                    runnable = name in registered || src != null,
                    runCount = stat?.count ?: 0,
                    lastStatus = stat?.status,
                    lastRunAt = stat?.at,
                )
            },
            runWindow = runWindow,
        )
    }

    /**
     * Project statistics folded out of the most recent [deriveLimit] runs. Recomputed on every listing
     * — derived projects are a projection over run history, never rows in the project store, so an
     * entry cannot outlive the runs that produced it.
     */
    private fun deriveStats(): Map<String, ProjectStat> {
        val stats = LinkedHashMap<String, ProjectStat>()
        // recent() is newest-first, so the first record seen for a name is its latest run. This method
        // relies on that ordering; if RunStore's contract ever changes, this must be re-derived.
        for (record in runs.recent(runWindow)) {
            val name = ProjectResolver.resolve(record) ?: continue
            val existing = stats[name]
            stats[name] = if (existing == null) {
                ProjectStat(count = 1, status = record.status, at = record.endedAt?.toString())
            } else {
                existing.copy(count = existing.count + 1)
            }
        }
        return stats
    }

    private data class ProjectStat(val count: Int, val status: String?, val at: String?)

    @PostMapping("/api/projects")
    suspend fun create(@RequestBody(required = false) request: CreateProjectRequest?): ResponseEntity<*> {
        val name = request?.name
        val text = request?.text
        if (name == null) {
            return badRequest("malformed request body — expected {\"name\": …}")
        }
        if (text == null && request.repo.isNullOrBlank()) {
            return badRequest("a project needs a descriptor or a repository to read one from")
        }
        if (!ProjectStore.isValidName(name)) {
            return badRequest("invalid project name (use letters, digits, and . _ -)")
        }
        if (store.exists(name)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("project already exists: $name"))
        }
        // A supplied descriptor must still parse before it is stored (032). A repo-hosted one is checked
        // but never blocking: the operator may be registering the project before the file exists (FR-007).
        if (text != null) {
            runCatching { PipelineDescriptor.parse(text) }
                .getOrElse { return badRequest(it.message ?: "invalid descriptor") }
        }
        val check = if (text == null) checkRepository(request.repo!!, request.branch) else null
        withContext(Dispatchers.IO) {
            if (text != null) store.save(name, text)
            // Persist the optional source (033) alongside the descriptor when a repo was supplied.
            store.saveSource(name, ProjectSource(request.repo, request.branch))
        }
        return ResponseEntity.ok(CreatedProject(name, check))
    }

    /** Resolves a would-be repo-hosted descriptor to report what was found. Never throws. */
    private suspend fun checkRepository(repo: String, branch: String?): DescriptorCheck {
        val ref = RepoRef.parse(repo)
            ?: return DescriptorCheck(false, message = "$repo is not a GitHub repository — paste a descriptor instead")
        val target = branch?.takeIf { it.isNotBlank() }
            ?: return DescriptorCheck(false, message = "a repo-hosted descriptor needs a branch")
        val client = clients.client()
            ?: return DescriptorCheck(
                false,
                message = "no GitHub token available — the project was created, but runs will fail until one is set",
            )
        return try {
            val sha = client.branchHead(ref, target)
                ?: return DescriptorCheck(false, message = "branch '$target' not found on ${ref.slug}")
            val text = client.fileAt(ref, descriptorFileName, sha)
                ?: return DescriptorCheck(false, message = "no $descriptorFileName on '$target' at ${ref.slug}")
            // Every parse failure, not just DescriptorException: two parser paths raise a bare
            // IllegalArgumentException / NumberFormatException (an empty `secrets:` entry, a timeout too
            // large for Long), and this check must never stop the project being created (FR-007).
            runCatching { PipelineDescriptor.parse(text) }.fold(
                onSuccess = { DescriptorCheck(true, pipeline = it.name, stages = it.stages.size) },
                onFailure = {
                    DescriptorCheck(
                        false,
                        message = "invalid descriptor from ${ref.slug}@$target:$descriptorFileName: ${it.message}",
                    )
                },
            )
        } catch (e: GitHubApiException) {
            // Reached GitHub, but it said no (bad token, rate limit, server error, ...).
            DescriptorCheck(false, message = "GitHub API returned HTTP ${e.statusCode} for ${ref.slug}")
        } catch (e: IOException) {
            // Never reached GitHub at all (DNS, connection refused, TLS, timeout) — a routine outcome for
            // an add-time check, since a token or network may not be ready yet (FR-007).
            DescriptorCheck(false, message = "GitHub unreachable — ${e.message}")
        } catch (e: IllegalArgumentException) {
            // The branch is operator-typed and reaches the client as a URL path segment. The REST client
            // encodes it, but this check takes any GitHubClient — belt and braces, so a request that
            // still cannot be addressed is a warning, never a 500 that leaves the project uncreated.
            DescriptorCheck(false, message = "could not address branch '$target' on ${ref.slug}: ${e.message}")
        }
    }

    @PostMapping("/api/projects/{name}/activate")
    suspend fun activate(@PathVariable name: String): ResponseEntity<*> = withContext(Dispatchers.IO) {
        // Seed first, exactly as list() does: if this is the very first request against a fresh server
        // (empty store, nothing active yet) and it happens to be an activate call for a derived project,
        // seeding still needs to run — otherwise the widened seedIfEmpty guard below (activeName() !=
        // null once we setActive(name)) would permanently block `default` from ever being registered.
        // Ordering is benign: seeding registers `default` and marks it active, then this call's own
        // store.setActive(name) below overwrites the active pointer to the requested project.
        seedIfEmpty()
        // Validate the name before it ever reaches the filesystem (store.get resolves it straight into a
        // path) — this guard is what keeps store.get from being handed an unsafe name, so it must run first.
        if (!ProjectStore.isValidName(name)) {
            return@withContext notFound(name)
        }
        val text = store.get(name)
        // A derived project (039) has runs but no stored descriptor, and a repo-only project (041/Task 6c)
        // is registered by its source sidecar alone — either way it can be made active, which scopes the
        // dashboard to it, but there is nothing to write as the live descriptor, and overwriting the
        // current one with an unrelated project's pipeline would be a footgun.
        if (text == null && name !in deriveStats().keys && !store.exists(name)) {
            return@withContext notFound(name)
        }
        if (text != null) {
            descriptor.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            Files.writeString(descriptor, text)
        }
        store.setActive(name)
        ResponseEntity.ok(ActiveProject(name))
    }

    private fun notFound(name: String): ResponseEntity<*> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("no such project: $name"))

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

    /**
     * On first use, register the current descriptor file as `default` and activate it. Skipped once
     * anything is active — not just once a project is registered — because (039) activating a derived
     * project sets an active pointer without ever registering it in the store; without this check, the
     * next listing would see an "empty" store and clobber that pointer back to `default`.
     */
    private fun seedIfEmpty() {
        if (store.list().isNotEmpty() || store.activeName() != null) return
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
