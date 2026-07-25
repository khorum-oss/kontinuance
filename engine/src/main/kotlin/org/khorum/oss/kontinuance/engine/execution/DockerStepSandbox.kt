package org.khorum.oss.kontinuance.engine.execution

/**
 * A [StepSandbox] that runs a step inside a container when it names an image (`Step.image`), and runs
 * it on the host unchanged otherwise. This is container-based **runner isolation**: the step's command
 * executes in the named image with the run's workspace bind-mounted, so it uses the image's toolchain
 * and cannot reach the host filesystem outside the workspace.
 *
 * The wrapper is `docker run --rm -v <workspace>:<workdir> -w <workdir> [-e SECRET...] <image> <argv>`:
 * - the run's isolated [StepContext.workingDir] (an absolute host path) is bind-mounted at
 *   [containerWorkdir], which is also the container's working directory, so relative commands
 *   (`./gradlew`, a checked-out tree) resolve exactly as they do on the host;
 * - the step's declared secrets are forwarded **by name only** (`-e NAME`, no value) so `docker`
 *   reads each value from the launched process's own environment (which [ProcessStepExecutor]
 *   populates with the scoped env) — secret values never appear on the argv, keeping them out of
 *   `ps` and any command echo. Host passthrough vars (PATH/HOME/…) are intentionally not forwarded,
 *   so the container keeps its own.
 *
 * `docker` is invoked as a host CLI binary, never a dependency (no new supply-chain surface); a
 * missing `docker` binary surfaces as a FAILED step naming `docker` via [ProcessStepExecutor]'s
 * launch handling. Actually starting containers requires a Docker daemon on the host and is exercised
 * in CI / on real hosts; the argv construction here is pure and unit-tested.
 *
 * @param containerWorkdir the in-container mount point and working directory for the workspace.
 */
class DockerStepSandbox(
    private val containerWorkdir: String = DEFAULT_WORKDIR,
) : StepSandbox {

    override fun wrap(baseArgv: List<String>, context: StepContext): List<String> {
        val image = context.step.image ?: return baseArgv
        return dockerRunArgv(image, baseArgv, context)
    }

    /** The `docker run` argv wrapping [baseArgv] for [image] in [context]. Pure. */
    fun dockerRunArgv(image: String, baseArgv: List<String>, context: StepContext): List<String> = buildList {
        add("docker")
        add("run")
        add("--rm")
        add("-v")
        add("${context.workingDir.toAbsolutePath()}:$containerWorkdir")
        add("-w")
        add(containerWorkdir)
        context.step.secrets.forEach { secret ->
            add("-e")
            add(secret.name)
        }
        add(image)
        addAll(baseArgv)
    }

    companion object {
        /** Where the run workspace is mounted (and the working directory) inside the container. */
        const val DEFAULT_WORKDIR: String = "/workspace"
    }
}
