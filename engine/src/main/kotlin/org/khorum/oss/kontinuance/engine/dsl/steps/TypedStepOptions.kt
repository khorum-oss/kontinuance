package org.khorum.oss.kontinuance.engine.dsl.steps

import kotlin.time.Duration

/**
 * The shared step envelope for the typed builders (`gradleStep`/`dockerStep`/`npmStep`) — the same
 * knobs the v0 `step { }` exposes, bundled so each typed builder takes them as one optional argument.
 *
 * @param timeout optional per-step deadline (must be > 0 when set).
 * @param enabled when `false` the step is SKIPPED (maps to the builder's `condition`).
 * @param secrets secret names injected into the step's scoped environment and masked in logs.
 * @param workingDir optional relative subdirectory resolved inside the step's isolated directory.
 */
data class TypedStepOptions(
    val timeout: Duration? = null,
    val enabled: Boolean = true,
    val secrets: List<String> = emptyList(),
    val workingDir: String? = null,
)
