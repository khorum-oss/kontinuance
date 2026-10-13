package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * The two things Kontinuance asks of a Gitea instance (043), over its REST API.
 *
 * **Which commit a branch points at** (`GET …/branches/{branch}`). A delivery's signature carries no
 * timestamp, so a captured push delivery can be re-sent, and replaying an old merge would redeliver an
 * old commit. Asking the source of truth is the check a signature cannot give.
 *
 * **Posting a commit status** (`POST …/statuses/{sha}`), which is how a run's verdict becomes something
 * Gitea's branch protection can require.
 *
 * Both return a [Result] rather than throwing: "Gitea is not configured", "Gitea did not answer" and
 * "Gitea said no" each have a different remedy, and none of them may escape as a 500.
 */
@Component
class GiteaApi(
    @param:Value("\${kontinuance.hooks.gitea.api-url:}") private val apiUrl: String,
    @param:Value("\${kontinuance.hooks.gitea.token:}") private val token: String,
    private val mapper: ObjectMapper,
) : CommitStatusPoster {
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

    /** The commit [branch] of [repo] currently points at. */
    suspend fun head(repo: RepoRef, branch: String): Result<String> =
        send("${repo.slug}/branches/$branch") { GET() }.mapCatching { body ->
            mapper.readTree(body).path("commit").path("id").takeIf { it.isString }?.stringValue()
                ?: error("Gitea's answer names no commit")
        }

    override suspend fun post(repo: RepoRef, sha: String, status: CommitStatusPoster.Status): Result<Unit> {
        val body = mapper.writeValueAsString(
            mapOf(
                "state" to status.state,
                "context" to status.context,
                "description" to status.description,
                "target_url" to status.targetUrl,
            ).filterValues { it != null },
        )
        return send("${repo.slug}/statuses/$sha") {
            header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
        }.map { }
    }

    // TooGenericExceptionCaught: every way this call can fail — refused connection, timeout, a malformed
    // URL — has the same consequence for the caller, and none of them may escape as a 500.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun send(path: String, method: HttpRequest.Builder.() -> HttpRequest.Builder): Result<String> {
        if (apiUrl.isBlank()) return failure("kontinuance.hooks.gitea.api-url is not set")
        return try {
            val request = HttpRequest.newBuilder(URI.create("${apiUrl.trimEnd('/')}/api/v1/repos/$path"))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .apply { if (token.isNotBlank()) header("Authorization", "token $token") }
                .method()
                .build()
            val response = withContext(Dispatchers.IO) { http.send(request, BodyHandlers.ofString()) }
            if (response.statusCode() in SUCCESS) {
                Result.success(response.body())
            } else {
                failure("Gitea answered HTTP ${response.statusCode()} for $path")
            }
        } catch (e: Exception) {
            failure("could not reach Gitea: ${e.message ?: e::class.simpleName}")
        }
    }

    private fun failure(reason: String): Result<String> = Result.failure(IllegalStateException(reason))

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(10)
        val SUCCESS = 200..299
    }
}
