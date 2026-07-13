package org.khorum.oss.kontinuance.engine.cli

import kotlinx.coroutines.runBlocking
import org.khorum.oss.kontinuance.engine.descriptor.DescriptorException
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import java.io.IOException
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Minimal command-line entry point: load a pipeline descriptor, run it in-process via the default
 * [PipelineEngine], print the outcome, and translate it to a process exit code.
 *
 * Exit codes: `0` = success, `1` = the pipeline finished in a failure state, `2` = a usage or
 * descriptor-loading error (missing / unreadable / malformed descriptor).
 *
 * With `--check`, the descriptor is loaded and its structure printed but **no step is executed**
 * (returns `0` if it parses, `2` if not) — a safe validation pass.
 */
object Runner {

    fun run(args: Array<String>, engine: PipelineEngine = PipelineEngine.default()): Int {
        val positional = args.filterNot { it.startsWith("--") }.toTypedArray()
        val pipeline = loadPipeline(positional) ?: return USAGE_ERROR
        return if (args.contains("--check")) checkOnly(pipeline) else execute(pipeline, engine)
    }

    /** Parse-only: print the pipeline's structure and return success without executing any step. */
    private fun checkOnly(pipeline: Pipeline): Int {
        println("descriptor OK: '${pipeline.name}' — ${pipeline.stages.size} stage(s)")
        pipeline.stages.forEach { stage ->
            println("  stage '${stage.name}':")
            stage.steps.forEach { step ->
                val secrets = if (step.secrets.isEmpty()) "" else " secrets=${step.secrets.map { it.name }}"
                val dir = step.workingDirHint?.let { " workingDir=$it" } ?: ""
                println("    - ${step.name} [${step.definition::class.simpleName}]$secrets$dir")
            }
        }
        return SUCCESS
    }

    private fun execute(pipeline: Pipeline, engine: PipelineEngine): Int {
        val result = runBlocking { engine.run(pipeline) }
        println("pipeline '${pipeline.name}' finished: ${render(result.status)}")
        return if (result.status.isFailure) FAILURE else SUCCESS
    }

    /** Resolve the descriptor path from [args] and load it; prints a usage message and returns null on error. */
    private fun loadPipeline(args: Array<String>): Pipeline? {
        val path = args.firstOrNull()
        if (path == null) {
            usage("missing descriptor path")
            return null
        }
        return try {
            PipelineDescriptor.load(Path.of(path))
        } catch (e: DescriptorException) {
            usage("invalid descriptor '$path': ${e.message}")
            null
        } catch (e: IOException) {
            usage("cannot read descriptor '$path': ${e.message}")
            null
        }
    }

    /** Human-readable one-liner for a terminal status (avoids the default object toString). */
    private fun render(status: PipelineStatus): String =
        if (status is PipelineStatus.Failed) {
            "Failed (step=${status.step}: ${status.reason})"
        } else {
            status::class.simpleName ?: status.toString()
        }

    private fun usage(message: String) {
        System.err.println("kontinuance: $message")
        System.err.println("usage: kontinuance [--check] <pipeline-descriptor.(yaml|yml)>")
    }

    private const val SUCCESS = 0
    private const val FAILURE = 1
    private const val USAGE_ERROR = 2
}

fun main(args: Array<String>) {
    exitProcess(Runner.run(args))
}
