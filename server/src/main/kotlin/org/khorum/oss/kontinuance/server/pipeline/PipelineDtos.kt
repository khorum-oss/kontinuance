package org.khorum.oss.kontinuance.server.pipeline

/**
 * `/api/runs/{id}/pipeline` DTOs, serialized by Jackson. `deps` is always empty — the engine models
 * order, not a DAG — but stays in the shape for a future DAG.
 */
data class PipelineResponse(val runId: String, val stages: List<PipelineStage>)

data class PipelineStage(val id: String, val name: String, val tasks: List<PipelineTask>)

data class PipelineTask(
    val id: String,
    val name: String,
    val tool: String,
    val status: String,
    val progress: Int,
    val deps: List<String> = emptyList(),
)
