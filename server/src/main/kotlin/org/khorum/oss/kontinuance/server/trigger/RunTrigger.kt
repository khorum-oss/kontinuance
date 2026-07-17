package org.khorum.oss.kontinuance.server.trigger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
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
 * immediately (so the run appears live in the UI's runs list via the SSE stream), then executes the
 * pipeline on the engine in the background and records the terminal record under the same id when it
 * finishes. Secrets are resolved from the environment (the engine's default `EnvSecretSource`). No auth
 * yet — this is the operator's own environment (consistent with the MVP's no-auth stance).
 */
@Component
class RunTrigger(
    private val store: RunStore,
    private val engine: PipelineEngine,
    private val scope: CoroutineScope,
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

        scope.launch {
            val terminal = runCatching {
                RunRecord.from(engine.run(pipeline), Instant.now(), trigger = "manual").copy(id = id)
            }.getOrElse {
                RunRecord(
                    id = id,
                    pipeline = pipeline.name,
                    status = "Failed",
                    reason = it.message,
                    startedAt = startedAt,
                    endedAt = Instant.now(),
                    trigger = "manual",
                )
            }
            store.record(terminal)
        }
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
