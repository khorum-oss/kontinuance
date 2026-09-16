package org.khorum.oss.kontinuance.server.service

/**
 * The context a run is recorded under: the repository it checks out (033), the commit that checkout is
 * pinned to (034/041), the project it belongs to (039), and how it was started.
 *
 * Carried as one value because every record a run writes has to agree on it — the immediate `Running`
 * record the runs list shows while the run is in flight, the terminal record, and the record a resumed
 * run writes after an approval gate. Each of those replaces the last by id, so a field missing from one
 * of them is not merely absent from that write: it erases what an earlier write had already recorded.
 */
data class RunContext(
    val repo: String? = null,
    val project: String? = null,
    val sha: String? = null,
    /**
     * How the run was started — `manual` (the dashboard), `github-actions` (a CI dispatch), and so on.
     *
     * Belongs here for the same reason repo/sha/project do: the engine does not know it, and the terminal
     * record replaces the `Running` one wholesale. Hardcoding it at the point of writing meant a run
     * started by CI was relabelled `manual` the moment it finished.
     */
    val trigger: String = "manual",
)
