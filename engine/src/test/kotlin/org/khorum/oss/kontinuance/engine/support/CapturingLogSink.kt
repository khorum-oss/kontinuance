package org.khorum.oss.kontinuance.engine.support

import org.khorum.oss.kontinuance.engine.logging.LogSink
import java.util.concurrent.CopyOnWriteArrayList

/** A thread-safe [LogSink] that records emitted lines for assertions. */
class CapturingLogSink : LogSink {
    private val recorded = CopyOnWriteArrayList<String>()

    override fun emit(line: String) {
        recorded.add(line)
    }

    fun lines(): List<String> = recorded.toList()

    fun text(): String = recorded.joinToString("\n")
}
