package org.khorum.oss.kontinuance.github.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers

/**
 * A thin [GitHubClient] over JDK 21's built-in [HttpClient] and the GitHub REST API. No third-party
 * HTTP or JSON dependency: JSON is parsed/emitted with the runtime `kotlinx-serialization-json`
 * (no generated serializers). [baseUrl] is overridable so tests point it at a local stand-in server.
 *
 * @param token the GitHub PAT (Bearer); it is a secret — never log it.
 * @param baseUrl the API root, default the public GitHub API.
 * @param http the JDK client (injectable for tests).
 */
class RestGitHubClient(
    private val token: String,
    private val baseUrl: String = "https://api.github.com",
    private val http: HttpClient = HttpClient.newHttpClient(),
) : GitHubClient {

    private val root = baseUrl.trimEnd('/')

    override suspend fun listOpenPullRequests(repo: RepoRef): List<PullRequest> {
        val response = send(get("$root/repos/${repo.slug}/pulls?state=open&per_page=100"))
        requireSuccess(response)
        return Json.parseToJsonElement(response.body()).jsonArray.map { element ->
            val pr = element.jsonObject
            val head = pr.getValue("head").jsonObject
            PullRequest(
                number = pr.getValue("number").jsonPrimitive.int,
                headSha = head.getValue("sha").jsonPrimitive.content,
                headRef = head.getValue("ref").jsonPrimitive.content,
                baseRef = pr.getValue("base").jsonObject.getValue("ref").jsonPrimitive.content,
            )
        }
    }

    override suspend fun branchHead(repo: RepoRef, branch: String): String? {
        val response = send(get("$root/repos/${repo.slug}/commits/$branch"))
        if (response.statusCode() == NOT_FOUND) return null
        requireSuccess(response)
        return Json.parseToJsonElement(response.body()).jsonObject.getValue("sha").jsonPrimitive.content
    }

    override suspend fun createCommitStatus(repo: RepoRef, sha: String, status: CommitStatus) {
        val body = buildJsonObject {
            put("state", status.state.wire)
            put("context", status.context)
            put("description", status.description)
            status.targetUrl?.let { put("target_url", it) }
        }.toString()
        val response = send(post("$root/repos/${repo.slug}/statuses/$sha", body))
        requireSuccess(response)
    }

    private fun get(url: String): HttpRequest = baseRequest(url).GET().build()

    private fun post(url: String, body: String): HttpRequest =
        baseRequest(url).POST(HttpRequest.BodyPublishers.ofString(body)).build()

    private fun baseRequest(url: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Content-Type", "application/json")

    private suspend fun send(request: HttpRequest): HttpResponse<String> =
        withContext(Dispatchers.IO) { http.send(request, BodyHandlers.ofString()) }

    private fun requireSuccess(response: HttpResponse<String>) {
        val code = response.statusCode()
        if (code !in SUCCESS_RANGE) {
            val retryAfter = response.headers().firstValue("Retry-After").orElse(null)?.toLongOrNull()
            throw GitHubApiException(code, "GitHub API returned HTTP $code", retryAfter)
        }
    }

    private companion object {
        val SUCCESS_RANGE = 200..299
        const val NOT_FOUND = 404
    }
}
