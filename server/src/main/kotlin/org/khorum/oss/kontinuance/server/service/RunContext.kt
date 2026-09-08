package org.khorum.oss.kontinuance.server.service

/**
 * The ownership a run is recorded under: the repository it checks out (033) and the project it belongs
 * to (039).
 *
 * Carried as one value because every record a run writes has to agree on it — the immediate `Running`
 * record the runs list shows while the run is in flight, the terminal record, and the record a resumed
 * run writes after an approval gate. Losing it on any one of those drops the run out of the
 * project-scoped runs list for as long as that record stands.
 */
data class RunContext(
    val repo: String? = null,
    val project: String? = null,
)
