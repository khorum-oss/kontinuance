package org.khorum.oss.kontinuance.engine.descriptor

import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.SecretRef
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import org.khorum.oss.kontinuance.engine.model.DockerStep
import org.khorum.oss.kontinuance.engine.model.GradleStep
import org.khorum.oss.kontinuance.engine.model.NpmStep
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * The declarative front-end of the hybrid (Option C) pipeline definition: parses a YAML
 * descriptor into the shared pipeline model.
 *
 * Parsing is **strict** — unknown keys, missing required keys, or malformed values raise a
 * [DescriptorException] identifying the location, and no step executes (FR-003). The produced
 * model is identical to the equivalent Kotlin-DSL definition (FR-002, SC-002).
 */
object PipelineDescriptor {

    private val load = Load(
        LoadSettings.builder().setAllowDuplicateKeys(false).build(),
    )

    private val PIPELINE_KEYS = setOf("name", "concurrency", "stages")
    private val STAGE_KEYS = setOf("name", "steps")
    private val DEFINITION_KEYS = setOf("run", "gradle", "docker", "npm")
    private val STEP_KEYS = setOf("name", "timeout", "when", "secrets", "workingDir") + DEFINITION_KEYS
    private val GRADLE_KEYS = setOf("tasks", "args", "useWrapper")
    private val DOCKER_KEYS = setOf("run", "build")
    private val DOCKER_RUN_KEYS = setOf("image", "command", "env", "volumes")
    private val DOCKER_BUILD_KEYS = setOf("context", "dockerfile", "tags", "buildArgs")
    private val NPM_KEYS = setOf("script", "install")
    private val NPM_INSTALL_KEYS = setOf("clean")

    /** Reads the descriptor at [path] and parses it into a [Pipeline]. */
    fun load(path: Path): Pipeline = parse(path.readText())

    /** Parses [yaml] into a [Pipeline], or throws [DescriptorException] on any error. */
    fun parse(yaml: String): Pipeline {
        val root = try {
            load.loadFromString(yaml)
        } catch (e: YamlEngineException) {
            throw DescriptorException("invalid YAML: ${e.message}", e)
        }
        val rootMap = asMap(root, "<root>")
        checkKeys(rootMap, setOf("pipeline"), "<root>")
        val pipelineMap = asMap(requireKey(rootMap, "pipeline", "<root>"), "pipeline")
        checkKeys(pipelineMap, PIPELINE_KEYS, "pipeline")

        val name = asString(requireKey(pipelineMap, "name", "pipeline"), "pipeline.name")
        val concurrency = pipelineMap["concurrency"]?.let { asInt(it, "pipeline.concurrency") } ?: 1
        val stages = asListOrEmpty(pipelineMap["stages"], "pipeline.stages")
            .mapIndexed { i, raw -> parseStage(raw, "pipeline.stages[$i]") }

        return construct("pipeline") { Pipeline(name, stages, concurrency) }
    }

    private fun parseStage(raw: Any?, path: String): Stage {
        val map = asMap(raw, path)
        checkKeys(map, STAGE_KEYS, path)
        val name = asString(requireKey(map, "name", path), "$path.name")
        val steps = asListOrEmpty(map["steps"], "$path.steps")
            .mapIndexed { i, stepRaw -> parseStep(stepRaw, "$path.steps[$i]") }
        return construct(path) { Stage(name, steps) }
    }

    private fun parseStep(raw: Any?, path: String): Step {
        val map = asMap(raw, path)
        checkKeys(map, STEP_KEYS, path)
        val name = asString(requireKey(map, "name", path), "$path.name")
        val definition = parseDefinition(map, path)
        val timeout = map["timeout"]?.let { parseDescriptorDuration(asString(it, "$path.timeout"), "$path.timeout") }
        val condition = map["when"]?.let { asBoolean(it, "$path.when") } ?: true
        val secrets = asListOrEmpty(map["secrets"], "$path.secrets")
            .mapIndexed { i, s -> SecretRef(asString(s, "$path.secrets[$i]")) }
        val workingDir = map["workingDir"]?.let { asString(it, "$path.workingDir") }
        return construct(path) {
            Step(
                name = name,
                definition = definition,
                timeout = timeout,
                condition = condition,
                secrets = secrets,
                workingDirHint = workingDir,
            )
        }
    }

    /** A step declares exactly one of `run`/`gradle`/`docker`/`npm`; zero or more than one is an error. */
    private fun parseDefinition(map: Map<String, Any?>, path: String): StepDefinition {
        val present = DEFINITION_KEYS.filter { map.containsKey(it) }
        if (present.size != 1) {
            throw DescriptorException(
                "$path: a step must declare exactly one of ${DEFINITION_KEYS.sorted()}, found ${present.sorted()}",
            )
        }
        return when (present.single()) {
            "run" -> RunStep(asString(requireKey(map, "run", path), "$path.run"))
            "gradle" -> parseGradle(asMap(map["gradle"], "$path.gradle"), "$path.gradle")
            "docker" -> parseDocker(asMap(map["docker"], "$path.docker"), "$path.docker")
            else -> parseNpm(asMap(map["npm"], "$path.npm"), "$path.npm")
        }
    }

    private fun parseGradle(map: Map<String, Any?>, path: String): StepDefinition {
        checkKeys(map, GRADLE_KEYS, path)
        val tasks = asStringList(requireKey(map, "tasks", path), "$path.tasks")
        val args = asStringList(map["args"], "$path.args")
        val useWrapper = map["useWrapper"]?.let { asBoolean(it, "$path.useWrapper") } ?: true
        return construct(path) { GradleStep(tasks, args, useWrapper) }
    }

    private fun parseDocker(map: Map<String, Any?>, path: String): StepDefinition {
        checkKeys(map, DOCKER_KEYS, path)
        val present = DOCKER_KEYS.filter { map.containsKey(it) }
        if (present.size != 1) {
            throw DescriptorException(
                "$path: docker must declare exactly one of ${DOCKER_KEYS.sorted()}, found ${present.sorted()}",
            )
        }
        return if (present.single() == "run") {
            val run = asMap(map["run"], "$path.run")
            checkKeys(run, DOCKER_RUN_KEYS, "$path.run")
            construct("$path.run") {
                DockerStep.run(
                    image = asString(requireKey(run, "image", "$path.run"), "$path.run.image"),
                    command = asStringList(requireKey(run, "command", "$path.run"), "$path.run.command"),
                    env = asStringMap(run["env"], "$path.run.env"),
                    volumes = asStringList(run["volumes"], "$path.run.volumes"),
                )
            }
        } else {
            val build = asMap(map["build"], "$path.build")
            checkKeys(build, DOCKER_BUILD_KEYS, "$path.build")
            construct("$path.build") {
                DockerStep.build(
                    context = build["context"]?.let { asString(it, "$path.build.context") } ?: ".",
                    dockerfile = build["dockerfile"]?.let { asString(it, "$path.build.dockerfile") },
                    tags = asStringList(build["tags"], "$path.build.tags"),
                    buildArgs = asStringMap(build["buildArgs"], "$path.build.buildArgs"),
                )
            }
        }
    }

    private fun parseNpm(map: Map<String, Any?>, path: String): StepDefinition {
        checkKeys(map, NPM_KEYS, path)
        val present = NPM_KEYS.filter { map.containsKey(it) }
        if (present.size != 1) {
            throw DescriptorException(
                "$path: npm must declare exactly one of ${NPM_KEYS.sorted()}, found ${present.sorted()}",
            )
        }
        return if (present.single() == "script") {
            construct(path) { NpmStep.script(asString(map["script"], "$path.script")) }
        } else {
            val install = asMap(map["install"], "$path.install")
            checkKeys(install, NPM_INSTALL_KEYS, "$path.install")
            val clean = install["clean"]?.let { asBoolean(it, "$path.install.clean") } ?: true
            construct(path) { NpmStep.install(clean) }
        }
    }

    /** A list of strings (empty when absent), e.g. Gradle `tasks`/`args` or a docker `command`. */
    private fun asStringList(value: Any?, path: String): List<String> =
        asListOrEmpty(value, path).mapIndexed { i, element -> asString(element, "$path[$i]") }

    /** A string→string mapping (empty when absent), e.g. docker `env`/`buildArgs`. */
    private fun asStringMap(value: Any?, path: String): Map<String, String> {
        if (value == null) return emptyMap()
        return asMap(value, path).mapValues { (key, raw) -> asString(raw, "$path.$key") }
    }
}

/** Runs a model constructor, translating its validation failures into [DescriptorException]. */
private inline fun <T> construct(location: String, block: () -> T): T =
    try {
        block()
    } catch (e: IllegalArgumentException) {
        throw DescriptorException("$location: ${e.message}", e)
    }

private fun checkKeys(map: Map<String, Any?>, allowed: Set<String>, path: String) {
    val unknown = map.keys - allowed
    if (unknown.isNotEmpty()) {
        throw DescriptorException("$path: unknown key(s) ${unknown.sorted()}; allowed: ${allowed.sorted()}")
    }
}

private fun requireKey(map: Map<String, Any?>, key: String, path: String): Any =
    map[key] ?: throw DescriptorException("$path: missing required key '$key'")

@Suppress("UNCHECKED_CAST")
private fun asMap(value: Any?, path: String): Map<String, Any?> {
    if (value !is Map<*, *>) throw DescriptorException("$path: expected a mapping, was ${typeName(value)}")
    val badKey = value.keys.firstOrNull { it !is String }
    if (badKey != null) throw DescriptorException("$path: non-string key '$badKey'")
    return value as Map<String, Any?>
}

private fun asListOrEmpty(value: Any?, path: String): List<Any?> =
    when (value) {
        null -> emptyList()
        is List<*> -> value
        else -> throw DescriptorException("$path: expected a list, was ${typeName(value)}")
    }

private fun asString(value: Any?, path: String): String =
    value as? String ?: throw DescriptorException("$path: expected a string, was ${typeName(value)}")

private fun asInt(value: Any?, path: String): Int =
    when (value) {
        is Int -> value
        is Long -> value.toInt()
        else -> throw DescriptorException("$path: expected an integer, was ${typeName(value)}")
    }

private fun asBoolean(value: Any?, path: String): Boolean =
    value as? Boolean ?: throw DescriptorException("$path: expected a boolean, was ${typeName(value)}")

private fun typeName(value: Any?): String = value?.javaClass?.simpleName ?: "null"
