package org.khorum.oss.kontinuance.server.service

import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding

/** Pushes one commit of a binding's tracked branch to its publish target. The seam [TrackedBranchPublisher] is tested through. */
fun interface CommitPublisher {

    /** A failure's message is operator-facing: it becomes the description of the commit's publish status. */
    suspend fun publish(binding: RepositoryBinding, sha: String): Result<Unit>
}
