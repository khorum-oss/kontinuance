package org.khorum.oss.kontinuance.server.config

import org.springframework.http.HttpMethod

/**
 * What the CI bearer token may reach — the whole of its authority, in one readable table.
 *
 * This is the first *scoped* identity in Kontinuance; the operator session remains all-or-nothing. Keep
 * this list short and literal rather than letting it grow into a role system: a CI caller starts one run,
 * watches that run, and cancels it. Nothing here should ever imply configuration access.
 *
 * Two exclusions are deliberate and easy to reintroduce by accident:
 *
 * - `/api/runs` and `/api/runs/stream` are GETs shaped like the run-read routes, but they are the *whole
 *   server's* run feeds, not the caller's own run.
 * - `/api/runs/trigger` is a POST shaped like a dispatch, but it runs whatever project is currently
 *   active — precisely the ambient behaviour a dispatch exists to avoid.
 */
object CiScope {

    private val ONE_RUN = Regex("^/api/runs/[^/]+$")
    private val ONE_RUN_LOGS = Regex("^/api/runs/[^/]+/logs(/stream)?$")
    private val ONE_RUN_CANCEL = Regex("^/api/runs/[^/]+/cancel$")

    /** Paths that match [ONE_RUN]'s shape but are not a run id. */
    private val NOT_A_RUN_ID = setOf("/api/runs/stream", "/api/runs/trigger")

    fun allows(path: String, method: HttpMethod?): Boolean = when {
        path in NOT_A_RUN_ID -> false
        path == "/api/ci/dispatch" -> method == HttpMethod.POST
        ONE_RUN_CANCEL.matches(path) -> method == HttpMethod.POST
        ONE_RUN.matches(path) || ONE_RUN_LOGS.matches(path) -> method == HttpMethod.GET
        else -> false
    }
}
