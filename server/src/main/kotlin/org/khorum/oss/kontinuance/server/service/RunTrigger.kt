package org.khorum.oss.kontinuance.server.service

import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.domain.project.DescriptorResolver
import org.khorum.oss.kontinuance.server.domain.project.ProjectSourceInjector
import org.khorum.oss.kontinuance.server.domain.project.Rejected
import org.khorum.oss.kontinuance.server.domain.project.Resolved
import org.khorum.oss.kontinuance.server.store.ProjectStore
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Manually triggers a pipeline run. Asks [DescriptorResolver] what pipeline the active project runs
 * (041), records a `Running` [RunRecord] immediately (so the run appears live in the UI's runs list via
 * the SSE stream), then hands off to [RunLauncher] to execute the pipeline in the background under the
 * same id — recording the terminal record when it finishes, or a paused `WaitingOnApproval` record if it
 * reaches an approval gate. Secrets are resolved from the environment. No auth yet (consistent with the
 * MVP's no-auth stance).
 */
@Component
class RunTrigger(
    private val store: RunStore,
    private val launcher: RunLauncher,
    private val projects: ProjectStore,
    private val resolver: DescriptorResolver,
) {

    suspend fun trigger(): Result {
        val resolution = resolver.resolve()
        val resolved = when (resolution) {
            is Rejected -> return Result.Rejected(resolution.reason)
            is Resolved -> resolution
        }

        // Drive the checkout from the active project's source (033). For a repo-hosted descriptor the
        // resolved commit is passed as the source value, which ProjectSourceInjector's existing SHA rule
        // pins as `sha` (034) — so the descriptor and the code always come from one commit.
        val activeProject = projects.activeName()
        val stored = activeProject?.let { projects.source(it) }
        val source = stored?.let { it.copy(branch = resolved.sha ?: it.branch) }
        val pipeline = ProjectSourceInjector.apply(resolved.pipeline, source)
        val repo = source?.repo?.takeIf { it.isNotBlank() }

        // Which project this run belongs to (039). The descriptor's own `project:` key still wins; failing
        // that the run belongs to the project it was launched under, which the reader cannot infer. Without
        // this the record carries no project and the reader falls back to the repo's short name — so a run
        // of project "foo" whose descriptor declares nothing, and whose source is "org/bar" or absent
        // entirely, never appeared under "foo" in the runs list even though "foo" is what started it.
        val project = pipeline.project ?: activeProject

        val id = "run-" + UUID.randomUUID().toString().substring(0, ID_LEN)
        val startedAt = Instant.now()
        store.record(
            RunRecord(
                id = id,
                pipeline = pipeline.name,
                status = "Running",
                startedAt = startedAt,
                repo = repo,
                sha = resolved.sha,
                trigger = "manual",
                project = project,
                // The pipeline as declared, every step Pending: the run's shape is known now, so the
                // pipeline view of a live run shows the real stages instead of nothing.
                stages = RunRecord.skeleton(pipeline),
            ),
        )
        launcher.launch(id, pipeline, startedAt, context = RunContext(repo, project, resolved.sha))
        return Result.Accepted(id)
    }

    sealed interface Result {
        data class Accepted(val id: String) : Result
        data class Rejected(val reason: String) : Result
    }

    private companion object {
        const val ID_LEN = 8
    }
}
