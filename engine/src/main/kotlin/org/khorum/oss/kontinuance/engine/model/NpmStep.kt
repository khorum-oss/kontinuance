package org.khorum.oss.kontinuance.engine.model

/** Which npm operation an [NpmStep] performs. */
enum class NpmMode { SCRIPT, INSTALL }

/**
 * Runs an npm operation as a first-class step, executed through the shared `ProcessBuilder` path by
 * [org.khorum.oss.kontinuance.engine.execution.steps.NpmStepExecutor].
 *
 * - [NpmMode.SCRIPT] runs `npm run <script>`; requires a non-empty [script].
 * - [NpmMode.INSTALL] installs dependencies: `npm ci` when [clean] (the reproducible default) or
 *   `npm install` otherwise.
 *
 * Prefer the [script] / [install] factories over the primary constructor.
 *
 * @param mode selects the npm operation.
 */
data class NpmStep(
    val mode: NpmMode,
    val script: String = "",
    val clean: Boolean = true,
) : StepDefinition {
    init {
        if (mode == NpmMode.SCRIPT) {
            require(script.isNotBlank()) { "NpmStep SCRIPT requires a non-empty script" }
        }
    }

    companion object {
        /** An `npm run <script>` step. */
        fun script(script: String): NpmStep = NpmStep(NpmMode.SCRIPT, script = script)

        /** An install step: `npm ci` when [clean] (default), else `npm install`. */
        fun install(clean: Boolean = true): NpmStep = NpmStep(NpmMode.INSTALL, clean = clean)
    }
}
