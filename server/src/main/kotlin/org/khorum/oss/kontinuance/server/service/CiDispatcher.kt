package org.khorum.oss.kontinuance.server.service

import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.domain.ci.CiBindings
import org.khorum.oss.kontinuance.server.domain.ci.CiDispatchRequest
import org.khorum.oss.kontinuance.server.domain.project.ProjectSource
import org.khorum.oss.kontinuance.server.domain.project.ProjectSourceInjector
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.readText

/**
 * Starts a pipeline run on behalf of an external CI caller (a GitHub Actions job), for one explicit
 * `(repo, sha)`.
 *
 * Distinct from [RunTrigger] on purpose. The trigger is the dashboard's button: it takes no arguments and
 * runs whatever project is *currently active*. A dispatch names its commit, and must never depend on which
 * project an operator happened to click last — that would be a live race between browsing the UI and a
 * build starting. The two share [RunLauncher]; nothing else.
 *
 * The repository's configured `prPipeline` is the only descriptor a dispatch can run. A supplied
 * `pipeline` is *confirmed* against it, never used to select one, so the endpoint cannot be walked into
 * running a delivery descriptor.
 */
@Component
class CiDispatcher(
    private val store: RunStore,
    private val launcher: RunLauncher,
    private val bindings: CiBindings,
) {

    suspend fun dispatch(request: CiDispatchRequest): Result {
        val binding = when (val resolution = resolve(request)) {
            is Refused -> return Result.Rejected(resolution.reason)
            is Bound -> resolution.binding
        }
        val declared = readPipeline(binding.prPipeline).getOrElse {
            return Result.Rejected("could not read ${binding.prPipeline}: ${it.message}")
        }

        // Pin the checkout to the dispatched commit: the injector's SHA rule (034) rewrites the first git
        // step's url + sha, so the descriptor and the code always come from the commit GitHub named.
        val pipeline = ProjectSourceInjector.apply(
            declared,
            ProjectSource(repo = "https://github.com/${binding.repo.slug}.git", branch = request.sha),
        )

        inFlight(binding.repo.slug, request.sha, pipeline.name)?.let { return Result.Existing(it) }

        return Result.Accepted(start(binding.repo.slug, request.sha, pipeline))
    }

    /** The binding this request may run, or why it may not. */
    private fun resolve(request: CiDispatchRequest): Resolution {
        if (!SHA.matches(request.sha)) {
            return Refused("sha must be a full 40-character commit id, got '${request.sha}'")
        }
        val configured = bindings.load().getOrElse {
            return Refused("dispatch allow-list unavailable: ${it.message}")
        }
        val binding = configured.firstOrNull { it.repo.slug.equals(request.repo, ignoreCase = true) }
            ?: return Refused("repository '${request.repo}' is not configured for dispatch")

        val asked = request.pipeline
        if (asked != null) {
            val name = binding.prPipeline.fileName.toString()
            if (asked != name && asked != binding.prPipeline.toString()) {
                return Refused("'$asked' is not the pipeline configured for ${binding.repo.slug} (expected '$name')")
            }
        }
        return Bound(binding)
    }

    /** Records the in-flight run and hands it to the engine; returns its id. */
    private fun start(repo: String, sha: String, pipeline: Pipeline): String {
        val id = "run-" + UUID.randomUUID().toString().substring(0, ID_LEN)
        val startedAt = Instant.now()
        store.record(
            RunRecord(
                id = id,
                pipeline = pipeline.name,
                status = "Running",
                startedAt = startedAt,
                repo = repo,
                sha = sha,
                trigger = TRIGGER,
                project = pipeline.project,
                stages = RunRecord.skeleton(pipeline),
            ),
        )
        launcher.launch(
            id,
            pipeline,
            startedAt,
            context = RunContext(repo = repo, project = pipeline.project, sha = sha, trigger = TRIGGER),
        )
        return id
    }

    /**
     * The id of a run already building this exact commit with this exact pipeline, or null.
     *
     * Re-running a workflow (or two pushes racing) must not put two ~14-minute builds of one commit on the
     * runner at once. Matching all three of repo/sha/pipeline means a *different* pipeline for the same
     * commit still gets its own run.
     */
    private fun inFlight(repo: String, sha: String, pipeline: String): String? =
        store.recent(IN_FLIGHT_WINDOW).firstOrNull {
            it.status == "Running" && it.repo == repo && it.sha == sha && it.pipeline == pipeline
        }?.id

    // The descriptor is operator-authored but still parsed input, and two parser paths raise bare
    // IllegalArgumentException/NumberFormatException rather than DescriptorException. A broken file must
    // refuse the dispatch, not escape as a 500 — the same stance as DescriptorResolver.parse.
    @Suppress("TooGenericExceptionCaught")
    private fun readPipeline(path: Path) = runCatching { PipelineDescriptor.parse(path.readText()) }

    /** What [resolve] concluded: the binding to run, or the caller-facing refusal. */
    private sealed interface Resolution
    private data class Bound(val binding: RepositoryBinding) : Resolution
    private data class Refused(val reason: String) : Resolution

    sealed interface Result {
        /** A new run was started. */
        data class Accepted(val id: String) : Result
        /** This commit is already building; watch [id] rather than starting another. */
        data class Existing(val id: String) : Result
        /** The dispatch was refused; [reason] is caller-facing. */
        data class Rejected(val reason: String) : Result
    }

    private companion object {
        val SHA = Regex("[0-9a-f]{40}")
        const val ID_LEN = 8
        const val TRIGGER = "github-actions"
        const val IN_FLIGHT_WINDOW = 50
    }
}
