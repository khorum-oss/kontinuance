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
 */
object Runner {

    fun run(args: Array<String>, engine: PipelineEngine = PipelineEngine.default()): Int {
        val pipeline = loadPipeline(args) ?: return USAGE_ERROR
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
        System.err.println("usage: kontinuance <pipeline-descriptor.(yaml|yml)>")
    }

    private const val SUCCESS = 0
    private const val FAILURE = 1
    private const val USAGE_ERROR = 2
}

fun main(args: Array<String>) {
    exitProcess(Runner.run(args))
}
