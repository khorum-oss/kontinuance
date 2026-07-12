package org.khorum.oss.kontinuance.engine.logging

import org.khorum.oss.kontinuance.engine.common.ExcludeFromCoverage

/**
 * The v0 [LogSink]: writes each line to process stdout.
 *
 * Excluded from coverage: a one-line side-effecting shim over `println` with no branching
 * logic of its own; the masking and streaming behavior it participates in is covered through
 * [MaskingLogSink] and the execution tests, which inject a capturing sink instead.
 */
@ExcludeFromCoverage("Thin println shim; behavior verified via injected capturing sinks")
class StdoutLogSink : LogSink {
    override fun emit(line: String) {
        println(line)
    }
}
