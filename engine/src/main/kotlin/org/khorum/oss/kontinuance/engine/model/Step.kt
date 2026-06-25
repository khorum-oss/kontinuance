package org.khorum.oss.kontinuance.engine.model

import kotlin.time.Duration

/**
 * A single unit of work within a [Stage].
 *
 * @param name non-empty; unique within its stage.
 * @param definition the typed payload describing what to execute (see [StepDefinition]).
 * @param timeout optional per-step deadline; when `null` a platform default applies. Must be > 0.
 * @param condition when `false` the step is SKIPPED (and its stage continues). v0 supports a
 *   plain boolean; richer expressions are a future extension.
 * @param secrets names of secrets injected into this step's scoped environment and masked in logs.
 * @param workingDirHint optional relative subdirectory resolved **inside** the step's isolated
 *   working directory; it must never be absolute nor escape via `..`.
 */
data class Step(
    val name: String,
    val definition: StepDefinition,
    val timeout: Duration? = null,
    val condition: Boolean = true,
    val secrets: List<SecretRef> = emptyList(),
    val workingDirHint: String? = null,
) {
    init {
        require(name.isNotBlank()) { "step name must be non-empty" }
        timeout?.let {
            require(it.isPositive()) { "step '$name' timeout must be > 0, was $it" }
        }
        workingDirHint?.let {
            require(isContainedRelativePath(it)) {
                "step '$name' workingDir '$it' must be a relative path inside the isolated directory"
            }
        }
    }

    private companion object {
        fun isContainedRelativePath(hint: String): Boolean {
            val isRelative = hint.isNotBlank() && !hint.startsWith("/") && !hint.startsWith("~")
            val hasNoTraversal = hint.split('/', '\\').none { it == ".." }
            return isRelative && hasNoTraversal
        }
    }
}
