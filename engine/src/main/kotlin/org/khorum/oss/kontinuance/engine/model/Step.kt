package org.khorum.oss.kontinuance.engine.model

import org.khorum.oss.konstellation.metaDsl.annotation.GeneratedDsl
import org.khorum.oss.konstellation.metaDsl.annotation.defaults.state.standard.DefaultEmptyList
import org.khorum.oss.konstellation.metaDsl.annotation.defaults.state.standard.DefaultTrue
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
 * @param image optional container image; when set, the step's command runs **inside** that image
 *   (runner isolation) with the workspace mounted, instead of directly on the host. When `null` the
 *   step runs on the host exactly as before. Must be non-blank when present.
 * @param runner optional container runner options (network/pull/uid, 030); applies only with an [image].
 */
@GeneratedDsl
data class Step(
    val name: String,
    val definition: StepDefinition,
    val timeout: Duration? = null,
    @DefaultTrue
    val condition: Boolean = true,
    @DefaultEmptyList
    val secrets: List<SecretRef> = emptyList(),
    val workingDirHint: String? = null,
    val image: String? = null,
    val runner: RunnerOptions? = null,
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
        image?.let {
            require(it.isNotBlank()) { "step '$name' image must be non-blank when set" }
        }
        // Guardrail: runner options are docker flags, so they only mean something inside a container.
        // Setting them without an image would silently run on the host with none of the isolation.
        if (runner != null) {
            require(image != null) { "step '$name' runner options require an image" }
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
