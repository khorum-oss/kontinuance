package org.khorum.oss.kontinuance.engine.model

/**
 * An ordered group of [Step]s within a [Pipeline].
 *
 * @param name non-empty; unique within its pipeline.
 * @param steps ordered; may be empty (an empty stage completes [PipelineStatus.Success]).
 */
data class Stage(
    val name: String,
    val steps: List<Step> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "stage name must be non-empty" }
        val duplicates = steps.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate step names in stage '$name': $duplicates" }
    }
}
