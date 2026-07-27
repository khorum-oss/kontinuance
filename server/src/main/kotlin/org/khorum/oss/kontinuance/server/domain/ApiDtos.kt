package org.khorum.oss.kontinuance.server.domain

import org.khorum.oss.kontinuance.persistence.RunRecord

/**
 * Response DTOs serialized by Jackson (the WebFlux JSON codec). Controllers return these directly rather
 * than hand-built JSON bytes; the ObjectMapper is configured (application.yml) to omit null properties and
 * write timestamps as ISO-8601, so the wire shape matches the 007+ contract. Kotlin `null` fields simply
 * disappear from the JSON, which is how optional fields (a run's repo/sha, an error message) stay absent.
 */

/** A single-field status/health message: `{ "status": … }` (health `ok`, cancel `cancelling`, …). */
data class StatusMessage(val status: String)

/** An error body: `{ "error": … }`. */
data class ErrorResponse(val error: String)

/** The accepted-trigger body: `{ "runId": … }`. */
data class RunIdResponse(val runId: String)

/** The runs list: `{ "runs": [ <run>… ] }`, each run the persisted [RunRecord] serialized by Jackson. */
data class RunsResponse(val runs: List<RunRecord>)

/** A run's recorded output: `{ "runId": …, "lines": [ … ] }` (018). */
data class RunLogsResponse(val runId: String, val lines: List<String>)
