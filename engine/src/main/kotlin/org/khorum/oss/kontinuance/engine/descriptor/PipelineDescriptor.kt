package org.khorum.oss.kontinuance.engine.descriptor

import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.SecretRef
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
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
    private val STEP_KEYS = setOf("name", "run", "timeout", "when", "secrets", "workingDir")

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
        val command = asString(requireKey(map, "run", path), "$path.run")
        val timeout = map["timeout"]?.let { parseDescriptorDuration(asString(it, "$path.timeout"), "$path.timeout") }
        val condition = map["when"]?.let { asBoolean(it, "$path.when") } ?: true
        val secrets = asListOrEmpty(map["secrets"], "$path.secrets")
            .mapIndexed { i, s -> SecretRef(asString(s, "$path.secrets[$i]")) }
        val workingDir = map["workingDir"]?.let { asString(it, "$path.workingDir") }
        return construct(path) {
            Step(
                name = name,
                definition = RunStep(command),
                timeout = timeout,
                condition = condition,
                secrets = secrets,
                workingDirHint = workingDir,
            )
        }
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
