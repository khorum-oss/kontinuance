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
 * Asks Gitea which commit a branch currently points at (`GET /api/v1/repos/{owner}/{repo}/branches/{branch}`).
 *
 * The webhook receiver needs this for one reason: a delivery's signature carries no timestamp, so a
 * captured push delivery can be re-sent, and replaying an old merge would redeliver an old commit. Asking
 * the source of truth is the check a signature cannot give (043).
 *
 * A [Result] rather than a nullable sha: "Gitea is not configured", "Gitea did not answer" and "that
 * branch does not exist" all have to refuse a delivery, and each has to say which it was.
 */
@Component
class GiteaBranchHeads(
    @param:Value("\${kontinuance.hooks.gitea.api-url:}") private val apiUrl: String,
    @param:Value("\${kontinuance.hooks.gitea.token:}") private val token: String,
    private val mapper: ObjectMapper,
) {
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

    // TooGenericExceptionCaught: every way this call can fail — refused connection, timeout, a body that
    // is not JSON — has the same consequence for the caller, and none of them may escape as a 500.
    @Suppress("TooGenericExceptionCaught")
    suspend fun head(repo: RepoRef, branch: String): Result<String> {
        if (apiUrl.isBlank()) return failure("kontinuance.hooks.gitea.api-url is not set")
        val target = "${apiUrl.trimEnd('/')}/api/v1/repos/${repo.slug}/branches/$branch"
        return try {
            val request = HttpRequest.newBuilder(URI.create(target)).timeout(TIMEOUT)
                .header("Accept", "application/json")
                .apply { if (token.isNotBlank()) header("Authorization", "token $token") }
                .GET().build()
            val response = withContext(Dispatchers.IO) { http.send(request, BodyHandlers.ofString()) }
            if (response.statusCode() != OK) {
                failure("Gitea answered HTTP ${response.statusCode()} for ${repo.slug}@$branch")
            } else {
                val sha = mapper.readTree(response.body()).path("commit").path("id")
                if (sha.isString) Result.success(sha.stringValue()) else failure("Gitea's answer names no commit")
            }
        } catch (e: Exception) {
            failure("could not reach Gitea: ${e.message ?: e::class.simpleName}")
        }
    }

    private fun failure(reason: String): Result<String> = Result.failure(IllegalStateException(reason))

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(10)
        const val OK = 200
    }
}
