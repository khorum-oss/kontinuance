package org.khorum.oss.kontinuance.server.trigger

import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/**
 * Manually triggers a pipeline run. Loads the configured descriptor, records a `Running` [RunRecord]
 * immediately (so the run appears live in the UI's runs list via the SSE stream), then hands off to
 * [RunLauncher] to execute the pipeline in the background under the same id — recording the terminal
 * record when it finishes, or a paused `WaitingOnApproval` record if it reaches an approval gate.
 * Secrets are resolved from the environment. No auth yet (consistent with the MVP's no-auth stance).
 */
@Component
class RunTrigger(
    private val store: RunStore,
    private val launcher: RunLauncher,
    @param:Value("\${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    fun trigger(): Result {
        if (!Files.isRegularFile(descriptor)) return Result.Rejected("no pipeline descriptor at $descriptor")
        val pipeline = runCatching { PipelineDescriptor.load(descriptor) }
            .getOrElse { return Result.Rejected("invalid descriptor: ${it.message}") }

        val id = "run-" + UUID.randomUUID().toString().substring(0, ID_LEN)
        val startedAt = Instant.now()
        store.record(
            RunRecord(id = id, pipeline = pipeline.name, status = "Running", startedAt = startedAt, trigger = "manual"),
        )
        launcher.launch(id, pipeline, startedAt)
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
