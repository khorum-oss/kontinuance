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

    fun resolve(record: RunRecord): String? {
        val explicit = record.project?.trim()?.ifEmpty { null }
        val fromRepo = record.repo?.substringAfterLast('/')?.trim()?.ifEmpty { null }
        val name = explicit ?: fromRepo ?: return null
        return name.takeIf { ProjectStore.isValidName(it) }
    }
}
