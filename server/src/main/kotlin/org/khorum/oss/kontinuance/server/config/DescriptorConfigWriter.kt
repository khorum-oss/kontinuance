package org.khorum.oss.kontinuance.server.config

import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates and persists an edited pipeline descriptor (027). A save is accepted only if the engine's own
 * strict [PipelineDescriptor] parser accepts the text — so the file on disk is always a runnable descriptor
 * and an invalid edit never overwrites a good one. On success the text is written to [path] (the same file
 * [ConfigController] serves and [org.khorum.oss.kontinuance.server.trigger.RunTrigger] runs) and the
 * refreshed `/api/config` projection is returned. No new dependency — the engine parser does the work.
 */
object DescriptorConfigWriter {

    sealed interface Result {
        /** Validated and written; [config] is the refreshed `/api/config` projection. */
        data class Written(val config: ConfigResponse) : Result

        /** Rejected before any write; [message] explains why (the parser's location-tagged error). */
        data class Invalid(val message: String) : Result
    }

    /** Validates [text], and only if it parses, writes it to [path] and returns the refreshed config. */
    fun write(path: Path, text: String): Result {
        runCatching { PipelineDescriptor.parse(text) }
            .getOrElse { return Result.Invalid(it.message ?: "invalid descriptor") }
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.writeString(path, text)
        // The file now exists, so the reader always yields a projection.
        val config = DescriptorConfigReader.read(path) ?: error("descriptor missing right after write")
        return Result.Written(config)
    }
}
