package org.khorum.oss.kontinuance.engine.model

/** Which docker sub-command a [DockerStep] invokes. */
enum class DockerMode { RUN, BUILD }

/**
 * Invokes the host `docker` CLI as a first-class step. Note this is *tool invocation*, not
 * container-based runner isolation (that remains a v1 concern); a `DockerStep` runs `docker` in the
 * same in-process, working-directory-isolated way as any other step.
 *
 * Exactly one mode is expressed per step:
 * - [DockerMode.RUN] runs a container: requires [image] and a non-empty [command]; [env] and
 *   [volumes] are optional `-e`/`-v` flags.
 * - [DockerMode.BUILD] builds an image from [context] (default `.`), optionally with a [dockerfile]
 *   (`-f`), [tags] (`-t`), and [buildArgs] (`--build-arg`).
 *
 * Prefer the [run] / [build] factories over the primary constructor.
 *
 * @param mode selects the docker sub-command.
 */
data class DockerStep(
    val mode: DockerMode,
    val image: String = "",
    val command: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val volumes: List<String> = emptyList(),
    val context: String = ".",
    val dockerfile: String? = null,
    val tags: List<String> = emptyList(),
    val buildArgs: Map<String, String> = emptyMap(),
) : StepDefinition {
    init {
        when (mode) {
            DockerMode.RUN -> {
                require(image.isNotBlank()) { "DockerStep RUN requires a non-empty image" }
                require(command.isNotEmpty()) { "DockerStep RUN requires a non-empty command" }
            }
            DockerMode.BUILD ->
                require(context.isNotBlank()) { "DockerStep BUILD requires a non-empty context" }
        }
    }

    companion object {
        /** A `docker run` step for [image] executing [command], with optional [env] and [volumes]. */
        fun run(
            image: String,
            command: List<String>,
            env: Map<String, String> = emptyMap(),
            volumes: List<String> = emptyList(),
        ): DockerStep = DockerStep(DockerMode.RUN, image = image, command = command, env = env, volumes = volumes)

        /** A `docker build` step over [context], with optional [dockerfile], [tags], and [buildArgs]. */
        fun build(
            context: String = ".",
            dockerfile: String? = null,
            tags: List<String> = emptyList(),
            buildArgs: Map<String, String> = emptyMap(),
        ): DockerStep =
            DockerStep(DockerMode.BUILD, context = context, dockerfile = dockerfile, tags = tags, buildArgs = buildArgs)
    }
}
