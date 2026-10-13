package org.khorum.oss.kontinuance.server.service

import org.khorum.oss.kontinuance.github.client.RepoRef

/** Posts a commit status to the host a repository lives on. The seam [GiteaStatusReporter] is tested through. */
fun interface CommitStatusPoster {

    suspend fun post(repo: RepoRef, sha: String, status: Status): Result<Unit>

    /** [state] is the wire value: `pending`, `success`, `failure` or `error`. */
    data class Status(val state: String, val context: String, val description: String, val targetUrl: String? = null)
}
