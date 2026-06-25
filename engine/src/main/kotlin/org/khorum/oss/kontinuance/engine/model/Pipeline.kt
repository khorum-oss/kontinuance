package org.khorum.oss.kontinuance.engine.model

/**
 * The unit submitted for a run: the shared model both definition front-ends
 * (YAML descriptor, Kotlin DSL) produce and the engine consumes.
 *
 * @param name non-empty; unique within a run request.
 * @param stages ordered; may be empty (an empty pipeline completes [PipelineStatus.Success]).
 * @param concurrency the maximum number of simultaneously RUNNING steps (K); must be >= 1.
 */
data class Pipeline(
    val name: String,
    val stages: List<Stage> = emptyList(),
    val concurrency: Int = 1,
) {
    init {
        require(name.isNotBlank()) { "pipeline name must be non-empty" }
        require(concurrency >= 1) { "pipeline '$name' concurrency must be >= 1, was $concurrency" }
        val duplicates = stages.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate stage names in pipeline '$name': $duplicates" }
    }
}
