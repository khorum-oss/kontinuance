package org.khorum.oss.kontinuance.engine.logging

/**
 * Append-only destination for streamed, line-oriented step output.
 *
 * v0 streams to process stdout; the abstraction keeps the transport pluggable (an SSE /
 * WebSocket sink and a persistent store arrive in v1) without touching executors.
 */
fun interface LogSink {
    /** Emits a single already-masked log line. */
    fun emit(line: String)
}
