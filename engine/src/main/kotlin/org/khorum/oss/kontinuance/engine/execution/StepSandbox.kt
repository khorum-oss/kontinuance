package org.khorum.oss.kontinuance.engine.execution

/**
 * Transforms a step's base argv (the command a [ProcessStepExecutor] would run on the host) into the
 * argv actually launched, giving the engine one place to run a step inside an isolated **runner** — a
 * container — instead of directly on the host.
 *
 * The default [HOST] sandbox is the identity: the command runs on the host exactly as before, so
 * introducing this seam changes no existing behavior. [DockerStepSandbox] wraps the command in
 * `docker run` when a step names a container image and is the identity otherwise, making isolation
 * opt-in per step (via `Step.image`) while leaving unset steps untouched.
 *
 * A [StepSandbox] is pure: it only describes the argv to launch; the actual process lifecycle
 * (isolation dir, scoped environment, streamed-and-masked output, timeout, tree-kill) stays in
 * [ProcessStepExecutor], so a wrapped command inherits all of it unchanged.
 */
fun interface StepSandbox {

    /** The argv to actually launch for [context], given the executor's [baseArgv]. */
    fun wrap(baseArgv: List<String>, context: StepContext): List<String>

    companion object {
        /** Runs the command on the host unchanged — the behavior before runner isolation existed. */
        val HOST: StepSandbox = StepSandbox { baseArgv, _ -> baseArgv }
    }
}
