package org.khorum.oss.kontinuance.engine.dsl.steps

import org.khorum.oss.kontinuance.engine.dsl.KontinuanceDsl
import org.khorum.oss.kontinuance.engine.model.DockerStep
import org.khorum.oss.kontinuance.engine.model.StepDslBuilder

/** Builder for a `docker run` step: an [image] plus a [command], with optional env and volumes. */
@KontinuanceDsl
class DockerRunBuilder {
    private val command = mutableListOf<String>()
    private val env = linkedMapOf<String, String>()
    private val volumes = mutableListOf<String>()

    /** The image to run, e.g. `node:20`. Required. */
    var image: String = ""

    /** Adds the container command, e.g. `command("node", "--version")`. Required. */
    fun command(vararg parts: String) {
        this.command += parts
    }

    /** Adds an `-e KEY=VALUE` environment entry. */
    fun env(key: String, value: String) {
        this.env[key] = value
    }

    /** Adds `-v` volume mounts, e.g. `volumes("/host:/container")`. */
    fun volumes(vararg mounts: String) {
        this.volumes += mounts
    }

    internal fun build(): DockerStep = DockerStep.run(image, command.toList(), env.toMap(), volumes.toList())
}

/** Builder for a `docker build` step: a [context] with optional [dockerfile], tags, and build args. */
@KontinuanceDsl
class DockerBuildBuilder {
    private val tags = mutableListOf<String>()
    private val buildArgs = linkedMapOf<String, String>()

    /** The build context directory (default `.`). */
    var context: String = "."

    /** An optional `-f` Dockerfile path. */
    var dockerfile: String? = null

    /** Adds `-t` image tags, e.g. `tags("myapp:ci")`. */
    fun tags(vararg tags: String) {
        this.tags += tags
    }

    /** Adds a `--build-arg KEY=VALUE`. */
    fun buildArg(key: String, value: String) {
        this.buildArgs[key] = value
    }

    internal fun build(): DockerStep = DockerStep.build(context, dockerfile, tags.toList(), buildArgs.toMap())
}

/**
 * Builder for a [dockerStep]: declares **exactly one** of [run] or [build], mirroring the
 * descriptor's `docker: { run: … }` / `docker: { build: … }` shape.
 */
@KontinuanceDsl
class DockerStepBuilder {
    private var definition: DockerStep? = null

    /** Configures a `docker run` step. */
    fun run(block: DockerRunBuilder.() -> Unit) {
        set(DockerRunBuilder().apply(block).build())
    }

    /** Configures a `docker build` step. */
    fun build(block: DockerBuildBuilder.() -> Unit) {
        set(DockerBuildBuilder().apply(block).build())
    }

    private fun set(step: DockerStep) {
        require(definition == null) { "dockerStep declares exactly one of run { } or build { }" }
        definition = step
    }

    internal fun definition(): DockerStep =
        requireNotNull(definition) { "dockerStep requires a run { } or build { } block" }
}

/**
 * Declares a first-class Docker step named [name] inside a `steps { }` block — the typed counterpart
 * to the descriptor's `docker:` key. [options] carries the shared step envelope
 * (`timeout`/`enabled`/`secrets`/`workingDir`), identical to the v0 `step { }`.
 */
fun StepDslBuilder.Group.dockerStep(
    name: String,
    options: TypedStepOptions = TypedStepOptions(),
    block: DockerStepBuilder.() -> Unit,
) {
    val definition = DockerStepBuilder().apply(block).definition()
    step { configureStep(name, definition, options) }
}
