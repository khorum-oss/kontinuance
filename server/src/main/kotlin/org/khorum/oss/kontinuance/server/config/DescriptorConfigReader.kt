package org.khorum.oss.kontinuance.server.config

import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.model.Pipeline
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads a real Kontinuance pipeline descriptor (`kontinuance.yml`) and projects it into the `/api/config`
 * contract shape ([ConfigResponse]): the raw source text plus a resolved plan derived from the parsed
 * [Pipeline] (stage / task counts, concurrency, the distinct step tools, and any publish/deploy stage).
 * Uses the engine's own [PipelineDescriptor] parser — no new dependency. Returns `null` when the
 * descriptor is absent so the controller can fall back to fixture data; when the file is present but does
 * not parse, the real text is still returned with a zeroed plan (so the operator sees their actual
 * descriptor).
 */
object DescriptorConfigReader {

    fun read(path: Path): ConfigResponse? {
        if (!Files.isRegularFile(path)) return null
        val text = Files.readString(path)
        val pipeline = runCatching { PipelineDescriptor.parse(text) }.getOrNull()
        return render(path.fileName.toString(), text, pipeline)
    }

    private fun render(source: String, text: String, pipeline: Pipeline?): ConfigResponse = ConfigResponse(
        source = source,
        text = text,
        plan = PlanSummary(
            stages = pipeline?.stages?.size ?: 0,
            tasks = pipeline?.stages?.sumOf { it.steps.size } ?: 0,
            maxParallel = pipeline?.concurrency ?: 1,
            toolchain = pipeline?.let(::tools) ?: "—",
            publish = pipeline?.let { stageMatching(it, "publish") } ?: "—",
            deploy = pipeline?.let { stageMatching(it, "deploy") } ?: "—",
        ),
    )

    /** Distinct step kinds (RunStep → "run", GradleStep → "gradle", …) across the pipeline. */
    private fun tools(pipeline: Pipeline): String =
        pipeline.stages.asSequence()
            .flatMap { it.steps.asSequence() }
            .map { it.definition::class.simpleName?.removeSuffix("Step")?.lowercase() ?: "step" }
            .distinct()
            .joinToString(" · ")
            .ifEmpty { "—" }

    private fun stageMatching(pipeline: Pipeline, needle: String): String =
        pipeline.stages.firstOrNull { it.name.contains(needle, ignoreCase = true) }?.name ?: "—"
}
