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
 * - builds a fresh temp working directory, resolving any `workingDir` hint **inside** it (FR-007);
 * - constructs a scoped environment from a small passthrough allow-list plus the resolved secrets,
 *   so arbitrary parent-process variables do not leak (FR-008);
 * - wraps the log sink in a [MaskingLogSink] so secrets are redacted in streamed output (SC-003);
 * - selects the executor from the [StepExecutorRegistry] and runs it;
 * - removes the working directory on any terminal status — including cancellation (FR-008, SC-004).
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
    private val baseEnvironment: Map<String, String> = defaultBaseEnvironment(),
) {

    /** Resolves, isolates, runs, and cleans up a single [step]; never returns a non-terminal status. */
    suspend fun run(step: Step): StepRun {
        if (!step.condition) {
            return StepRun(step.name, PipelineStatus.Skipped)
        }
        val resolved = resolveSecrets(step)
        val masker = SecretMasker(resolved.values)
        val maskingSink = MaskingLogSink(masker, logSink)
        val root = Files.createTempDirectory(WORKDIR_PREFIX)
        return try {
            val workingDir = resolveWorkingDir(root, step.workingDirHint)
            val context = StepContext(step, workingDir, baseEnvironment + resolved, maskingSink)
            registry.executorFor(step.definition).execute(context)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun resolveSecrets(step: Step): Map<String, String> =
        step.secrets.associate { ref ->
            ref.name to (secrets.resolve(ref.name) ?: throw UnresolvedSecretException(ref.name))
        }

    private fun resolveWorkingDir(root: Path, hint: String?): Path {
        if (hint == null) return root
        val resolved = root.resolve(hint).normalize()
        require(resolved.startsWith(root)) {
            "workingDir '$hint' escapes the isolated directory"
        }
        Files.createDirectories(resolved)
        return resolved
    }

    companion object {
        private const val WORKDIR_PREFIX = "knt-step-"

        /** Environment variables passed through to every step so commands resolve, without leaking the rest. */
        private val PASSTHROUGH = listOf("PATH", "HOME", "LANG", "LC_ALL", "TMPDIR")

        /** Builds the default scoped environment: only the passthrough allow-list from the parent process. */
        fun defaultBaseEnvironment(): Map<String, String> {
            val parent = System.getenv()
            return PASSTHROUGH.mapNotNull { key -> parent[key]?.let { key to it } }.toMap()
        }
    }
}
