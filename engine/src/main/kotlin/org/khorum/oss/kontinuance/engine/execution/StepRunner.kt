package org.khorum.oss.kontinuance.engine.execution

import org.khorum.oss.kontinuance.engine.logging.LogSink
import org.khorum.oss.kontinuance.engine.logging.MaskingLogSink
import org.khorum.oss.kontinuance.engine.logging.SecretMasker
import org.khorum.oss.kontinuance.engine.logging.StdoutLogSink
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepRun
import org.khorum.oss.kontinuance.engine.secret.SecretSource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Engine-side orchestration around a single step: it prepares the isolation the contract promises,
 * dispatches to the type-appropriate [StepExecutor], and guarantees cleanup.
 *
 * For each step it:
 * - resolves the step's secrets (a missing secret fails fast with [UnresolvedSecretException]);
 * - runs in the run's shared [workspace] directory, resolving any `workingDir` hint **inside** it so it
 *   cannot escape to the host; all steps of a run share this directory (a checkout persists across steps);
 * - constructs a scoped environment from a small passthrough allow-list plus the resolved secrets,
 *   so arbitrary parent-process variables do not leak (FR-008);
 * - wraps the log sink in a [MaskingLogSink] so secrets are redacted in streamed output (SC-003);
 * - selects the executor from the [StepExecutorRegistry] and runs it.
 *
 * The workspace itself is created and removed by the engine once per run, not per step.
 *
 * @param registry the executor registry.
 * @param secrets the secret backing used to resolve referenced secrets.
 * @param logSink the downstream (unmasked) sink; defaults to stdout.
 * @param baseEnvironment the scoped environment shared by every step before secrets are injected.
 */
class StepRunner(
    private val registry: StepExecutorRegistry,
    private val secrets: SecretSource,
    private val logSink: LogSink = StdoutLogSink(),
    private val workspace: Path,
    private val baseEnvironment: Map<String, String> = defaultBaseEnvironment(),
) {

    /**
     * Resolves, scopes, and runs a single [step] in the run's shared [workspace]; never returns a
     * non-terminal status. All steps of a run share this one directory (so a checkout persists across
     * steps); the engine creates it per run and removes it when the run ends.
     */
    suspend fun run(step: Step): StepRun {
        if (!step.condition) {
            return StepRun(step.name, PipelineStatus.Skipped)
        }
        val resolved = resolveSecrets(step)
        val masker = SecretMasker(resolved.values)
        val maskingSink = MaskingLogSink(masker, logSink)
        val workingDir = resolveWorkingDir(workspace, step.workingDirHint)
        val context = StepContext(step, workingDir, baseEnvironment + resolved, maskingSink)
        return registry.executorFor(step.definition).execute(context)
    }

    private fun resolveSecrets(step: Step): Map<String, String> =
        step.secrets.associate { ref ->
            ref.name to (secrets.resolve(ref.name) ?: throw UnresolvedSecretException(ref.name))
        }

    private fun resolveWorkingDir(root: Path, hint: String?): Path {
        if (hint == null) return root
        val resolved = root.resolve(hint).normalize()
        require(resolved.startsWith(root)) {
            "workingDir '$hint' escapes the workspace"
        }
        Files.createDirectories(resolved)
        return resolved
    }

    companion object {
        /** Environment variables passed through to every step so commands resolve, without leaking the rest. */
        private val PASSTHROUGH = listOf("PATH", "HOME", "LANG", "LC_ALL", "TMPDIR")

        /** Builds the default scoped environment: only the passthrough allow-list from the parent process. */
        fun defaultBaseEnvironment(): Map<String, String> {
            val parent = System.getenv()
            return PASSTHROUGH.mapNotNull { key -> parent[key]?.let { key to it } }.toMap()
        }
    }
}
