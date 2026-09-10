package org.khorum.oss.kontinuance.server.domain.project

import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.server.store.ProjectStore

/**
 * The single rule for "which project does this run belong to?" (039).
 *
 * Precedence: the pipeline's explicit `project`, else the repository's short name (the segment after
 * the final `/`), else none. The pipeline **name** is deliberately not a fallback — it would split one
 * application's PR gate, delivery, and promotion pipelines into three separate projects.
 *
 * A resolved name must satisfy [ProjectStore.isValidName], because derived names reach the `.active`
 * file and a path variable; an unsafe name resolves to none rather than being sanitized into
 * something that no longer identifies the same thing.
 */
object ProjectResolver {

    /**
     * Which project a run being launched belongs to — the **write** side of the same question [resolve]
     * answers for readers. The project the run was launched under wins; a descriptor's own `project:` key
     * only names a run that nothing was active for.
     *
     * This exists so the precedence lives in exactly one place. It was previously spelled out separately in
     * `RunTrigger` and again in `RunLauncher`, the two disagreed, and the launcher's terminal write re-filed
     * every run under the descriptor's name — so a run executed, persisted, reported Success, and never
     * appeared in the runs list, which is scoped to the active project by exact match. Every writer calls
     * this; none re-derives it.
     *
     * A repo-hosted descriptor (041) lives in a repository the operator may not control, which is why its
     * key does not get to decide where the operator's runs are filed.
     */
    fun forLaunch(activeProject: String?, descriptorProject: String?): String? =
        activeProject?.trim()?.ifEmpty { null } ?: descriptorProject?.trim()?.ifEmpty { null }

    fun resolve(record: RunRecord): String? {
        val explicit = record.project?.trim()?.ifEmpty { null }
        val fromRepo = record.repo?.substringAfterLast('/')?.trim()?.ifEmpty { null }
        val name = explicit ?: fromRepo ?: return null
        return name.takeIf { ProjectStore.isValidName(it) }
    }
}
