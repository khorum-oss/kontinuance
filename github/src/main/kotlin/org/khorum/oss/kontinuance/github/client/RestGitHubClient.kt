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
import java.nio.charset.StandardCharsets

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
        val response = send(get("$root/repos/${repo.slug}/commits/${encodePath(branch)}"))
        if (response.statusCode() == NOT_FOUND) return null
        requireSuccess(response)
        return Json.parseToJsonElement(response.body()).jsonObject.getValue("sha").jsonPrimitive.content
    }

    override suspend fun fileAt(repo: RepoRef, path: String, ref: String): String? {
        // The ref is a single query value, so it is encoded whole — a `/` in it becomes %2F rather than
        // splitting the parameter in two.
        val target = "$root/repos/${repo.slug}/contents/${encodePath(path)}?ref=${encodeSegment(ref)}"
        // The raw media type returns file contents verbatim, so no base64 decode step is needed.
        val request = baseRequest(target, RAW_ACCEPT)
            .GET()
            .build()
        val response = send(request)
        if (response.statusCode() == NOT_FOUND) return null
        requireSuccess(response)
        return response.body()
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

    private fun baseRequest(url: String, accept: String = DEFAULT_ACCEPT): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", accept)
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
        const val DEFAULT_ACCEPT = "application/vnd.github+json"
        const val RAW_ACCEPT = "application/vnd.github.raw"
    }
}

/** RFC 3986 §2.3 unreserved characters — the only ones that never need escaping in a URI. */
private val UNRESERVED = (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '.', '_', '~')).toSet()
private const val HEX = "0123456789ABCDEF"
private const val HEX_SHIFT = 4
private const val HEX_MASK = 0xF

/**
 * Percent-encodes [value] as one URI path segment, RFC 3986 rules: everything outside the unreserved set
 * is escaped. Deliberately **not** [java.net.URLEncoder], which is *form* encoding and turns a space into
 * `+` — a character GitHub would read literally.
 *
 * Values reaching here are operator-typed (a branch from the connect form) and git permits plenty of
 * characters that are illegal in a URI — `%`, a space, `|`, `{}` — so without this `URI.create` throws
 * before a request is ever sent.
 */
private fun encodeSegment(value: String): String {
    // A segment of only dots is a URI dot-segment, not a name: left raw, a branch like
    // "x/../../../user/repos" would walk the authenticated API to another endpoint. Git forbids ".." in a
    // ref anyway, so encoding it costs nothing real.
    if (value.isNotEmpty() && value.all { it == '.' }) return value.replace(".", "%2E")
    val encoded = StringBuilder(value.length)
    for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
        val code = byte.toInt()
        val char = code.toChar()
        if (char in UNRESERVED) {
            encoded.append(char)
        } else {
            encoded.append('%').append(HEX[(code shr HEX_SHIFT) and HEX_MASK]).append(HEX[code and HEX_MASK])
        }
    }
    return encoded.toString()
}

/** [encodeSegment] applied per `/`-separated segment, so `feature/foo` stays two path segments. */
private fun encodePath(value: String): String = value.split('/').joinToString("/") { encodeSegment(it) }
