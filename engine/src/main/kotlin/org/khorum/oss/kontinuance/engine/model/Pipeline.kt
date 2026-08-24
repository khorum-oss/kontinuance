package org.khorum.oss.kontinuance.engine.model

import org.khorum.oss.konstellation.metaDsl.annotation.GeneratedDsl
import org.khorum.oss.konstellation.metaDsl.annotation.ListDsl
import org.khorum.oss.konstellation.metaDsl.annotation.RootDsl
import org.khorum.oss.konstellation.metaDsl.annotation.defaults.DefaultValue
import org.khorum.oss.konstellation.metaDsl.annotation.defaults.state.standard.DefaultEmptyList

/**
 * The unit submitted for a run: the shared model both definition front-ends
 * (YAML descriptor, Kotlin DSL) produce and the engine consumes.
 *
 * The Kotlin DSL front-end is generated from these annotations by the Konstellation meta-DSL
 * (the `engine.dsl` package); the generated `pipeline { }` builder produces this exact model.
 *
 * @param name non-empty; unique within a run request.
 * @param stages ordered; may be empty (an empty pipeline completes [PipelineStatus.Success]).
 * @param concurrency the maximum number of simultaneously RUNNING steps (K); must be >= 1.
 * @param project optional owning project name; groups several pipelines (a PR gate, a delivery
 *   pipeline, a promotion) under one project in the dashboard. When `null` the project is inferred
 *   from the run's repository. Must be non-blank when present.
 */
@GeneratedDsl
@RootDsl(name = "pipeline", alias = "")
data class Pipeline(
    val name: String,
    @ListDsl
    @DefaultEmptyList
    val stages: List<Stage> = emptyList(),
    @DefaultValue("1")
    val concurrency: Int = 1,
    val project: String? = null,
) {
    init {
        require(name.isNotBlank()) { "pipeline name must be non-empty" }
        require(concurrency >= 1) { "pipeline '$name' concurrency must be >= 1, was $concurrency" }
        val duplicates = stages.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate stage names in pipeline '$name': $duplicates" }
        project?.let {
            require(it.isNotBlank()) { "pipeline '$name' project must be non-blank when set" }
        }
    }
}
