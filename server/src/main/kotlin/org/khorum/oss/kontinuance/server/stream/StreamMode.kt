package org.khorum.oss.kontinuance.server.stream

/**
 * How the live streams (`/api/runs/stream`, `/api/runs/{id}/logs/stream`) learn about changes.
 *
 * - [POLL] — re-read the shared store on a timer (the default; correct for every topology, including a
 *   separate `kontinuance-ci` writer process, since it observes any writer).
 * - [PUSH] — react immediately to an in-process change signal ([RunChangeNotifier]) **and** keep the poll
 *   as a fallback, so in-process writes (e.g. a server-triggered run) surface with no poll latency while
 *   out-of-process writes are still caught by the timer. Polling therefore never stops in push mode — it
 *   is a strictly-additive wakeup, and [POLL] remains selectable for the plain timer-only behavior.
 */
enum class StreamMode {
    POLL,
    PUSH,
    ;

    companion object {
        /** Parses the configured mode; anything unrecognized falls back to the safe [POLL] default. */
        fun from(raw: String?): StreamMode =
            when (raw?.trim()?.lowercase()) {
                "push" -> PUSH
                else -> POLL
            }
    }
}
