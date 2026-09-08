# Repo-hosted Descriptors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an operator connect a project with only a name, repository and branch, and have Kontinuance read `kontinuance.yml` out of that repository at run time.

**Architecture:** A new `DescriptorResolver` becomes the single answer to "what pipeline does this project run?" on the manual trigger path. A stored descriptor short-circuits it; otherwise the project's branch is resolved to a commit SHA through the existing `GitHubClient` seam, the descriptor is fetched at that SHA, and the checkout is pinned to the same commit using 034's existing SHA support. Resolution happens entirely before a run record is created, so `RunTrigger` keeps returning a clean `Rejected` for anything broken.

**Tech Stack:** Kotlin/JVM 21, Gradle, JUnit 5 + `kotlin.test`, kotlinx-coroutines, kotlinx-serialization-json (runtime parsing only), Spring Boot 4.1 WebFlux with coroutines, SvelteKit 5 (runes), Vitest, Playwright.

**Spec:** `specs/041-repo-hosted-descriptor/spec.md`

## Global Constraints

- **No new dependency in any module** (FR-012). JSON is parsed with the runtime `kotlinx.serialization.json` API already used in `RestGitHubClient`; HTTP uses the JDK's `HttpClient`.
- **Dependency verification stays enabled** (`gradle/verification-metadata.xml`). Nothing in this plan should require a new trust entry; if one appears, stop — it means a dependency crept in.
- **No test touches the real network.** GitHub is exercised through `RecordingGitHubClient` (unit) or `FakeGitHubServer` (integration).
- **The GitHub token is a secret.** It is resolved by name through `GitHubTokenStore.resolve`, never logged, and never returned by any read path.
- **`./gradlew check` must pass** — it runs tests, detekt and kover verification across every module.
- Web checks: `pnpm -C web test`, `pnpm -C web exec svelte-check`, `pnpm -C web exec playwright test`.
- Descriptors are Kontinuance's own schema — never copy shapes from another CI system.

## File Structure

**`github` module** — the GitHub seam only.
- `client/GitHubModels.kt` (modify) — `RepoRef.parse` lives with `RepoRef`.
- `client/GitHubClient.kt` (modify) — one new interface method.
- `client/RestGitHubClient.kt` (modify) — its REST implementation.
- `support/RecordingGitHubClient.kt` (modify, test) — the fake must satisfy the widened interface.

**`server` module** — resolution and the endpoints.
- `domain/project/DescriptorResolver.kt` (create) — the resolution rule. Pure apart from the injected seams, so it is exhaustively unit-testable.
- `domain/project/GitHubClientProvider.kt` (create) — a one-method seam producing an authenticated client, or `null` when no token is available. Keeps token plumbing out of the resolver.
- `service/RunTrigger.kt` (modify) — swaps its file read for the resolver.
- `controller/TriggerController.kt` (modify) — becomes `suspend`.
- `controller/ProjectController.kt` (modify) — `create` accepts a project with no descriptor text.
- `controller/config/ConfigController.kt` (modify) — reports origin, adds the revert endpoint.
- `domain/project/ProjectDtos.kt`, `domain/ConfigDtos.kt` (modify) — wire shapes.

**`web`** — `Login.svelte` is 809 lines and already holds the entry screen, the picker and the add form. Task 9 extracts the add form into `components/AddProject.svelte` rather than growing it further.

---

### Task 1: Parse a repository URL into a `RepoRef`

**Files:**
- Modify: `github/src/main/kotlin/org/khorum/oss/kontinuance/github/client/GitHubModels.kt`
- Test: `github/src/test/kotlin/org/khorum/oss/kontinuance/github/client/RepoRefParseTest.kt` (create)

**Interfaces:**
- Consumes: nothing.
- Produces: `RepoRef.parse(url: String): RepoRef?` — a companion function on the existing `RepoRef`. Returns `null` for any URL that is not a GitHub repository. Task 3 uses that `null` as its "requires a stored descriptor" signal.

- [ ] **Step 1: Write the failing test**

Create `github/src/test/kotlin/org/khorum/oss/kontinuance/github/client/RepoRefParseTest.kt`:

```kotlin
package org.khorum.oss.kontinuance.github.client

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepoRefParseTest {

    @Test
    fun `parses the https form a stored source holds`() {
        assertEquals(
            RepoRef("khorum-oss", "spektr"),
            RepoRef.parse("https://github.com/khorum-oss/spektr"),
        )
    }

    @Test
    fun `tolerates a git suffix and a trailing slash`() {
        val expected = RepoRef("khorum-oss", "spektr")
        assertEquals(expected, RepoRef.parse("https://github.com/khorum-oss/spektr.git"))
        assertEquals(expected, RepoRef.parse("https://github.com/khorum-oss/spektr/"))
        assertEquals(expected, RepoRef.parse("https://github.com/khorum-oss/spektr.git/"))
    }

    @Test
    fun `parses the ssh form`() {
        assertEquals(
            RepoRef("khorum-oss", "spektr"),
            RepoRef.parse("git@github.com:khorum-oss/spektr.git"),
        )
    }

    @Test
    fun `returns null for a repository that is not on GitHub`() {
        assertNull(RepoRef.parse("https://gitlab.com/khorum-oss/spektr"))
        assertNull(RepoRef.parse("https://example.test/khorum-oss/spektr"))
    }

    @Test
    fun `returns null for a url with no owner and name`() {
        assertNull(RepoRef.parse("https://github.com/khorum-oss"))
        assertNull(RepoRef.parse("not a url"))
        assertNull(RepoRef.parse(""))
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :github:test --tests "*RepoRefParseTest*"`
Expected: FAIL — compilation error, `parse` is not a member of `RepoRef.Companion`.

- [ ] **Step 3: Implement `parse`**

In `GitHubModels.kt`, add a companion to the existing `RepoRef` (keep the existing `slug` and `init`):

```kotlin
data class RepoRef(val owner: String, val name: String) {
    /** The `owner/name` slug used in API paths and logs. */
    val slug: String get() = "$owner/$name"

    init {
        require(owner.isNotBlank()) { "repo owner must be non-empty" }
        require(name.isNotBlank()) { "repo name must be non-empty" }
    }

    companion object {
        private val HTTPS = Regex("^https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$")
        private val SSH = Regex("^git@github\\.com:([^/]+)/([^/]+?)(?:\\.git)?/?$")

        /**
         * The [RepoRef] a repository URL names, or `null` when the URL is not a GitHub repository.
         *
         * `null` is a routine answer, not an error: a project may legitimately point at another host,
         * in which case it simply cannot use a repo-hosted descriptor (041, FR-002).
         */
        fun parse(url: String): RepoRef? {
            val match = HTTPS.find(url.trim()) ?: SSH.find(url.trim()) ?: return null
            val (owner, name) = match.destructured
            return if (owner.isBlank() || name.isBlank()) null else RepoRef(owner, name)
        }
    }
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew :github:test --tests "*RepoRefParseTest*"`
Expected: PASS — 5 tests.

- [ ] **Step 5: Commit**

```bash
git add github/src/main/kotlin/org/khorum/oss/kontinuance/github/client/GitHubModels.kt \
        github/src/test/kotlin/org/khorum/oss/kontinuance/github/client/RepoRefParseTest.kt
git commit -m "feat(041): parse a repository URL into a RepoRef"
```

---

### Task 2: Fetch one file from a repository at a ref

**Files:**
- Modify: `github/src/main/kotlin/org/khorum/oss/kontinuance/github/client/GitHubClient.kt`
- Modify: `github/src/main/kotlin/org/khorum/oss/kontinuance/github/client/RestGitHubClient.kt`
- Modify: `github/src/test/kotlin/org/khorum/oss/kontinuance/github/support/RecordingGitHubClient.kt`
- Test: `github/src/test/kotlin/org/khorum/oss/kontinuance/github/client/RestGitHubClientIT.kt` (add cases)

**Interfaces:**
- Consumes: `RepoRef` from Task 1.
- Produces: `suspend fun GitHubClient.fileAt(repo: RepoRef, path: String, ref: String): String?` — the file's contents, or `null` when it does not exist at that ref. Throws `GitHubApiException` on any other non-success status. `RecordingGitHubClient` gains a `files: Map<String, String>` constructor parameter keyed by `path`, used by Task 3's tests.

- [ ] **Step 1: Write the failing test**

Append to `RestGitHubClientIT.kt` (inside the existing class, which already defines `private val repo = RepoRef("khorum-oss", "kontinuance")`):

```kotlin
    @Test
    fun `fetches a file's contents at a ref`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/contents/.+", body = "pipeline:\n  name: \"demo\"\n")
            val client = RestGitHubClient(token = "t0k3n", baseUrl = server.baseUrl)

            val text = client.fileAt(repo, "kontinuance.yml", "abc123")

            assertEquals("pipeline:\n  name: \"demo\"\n", text)
            val request = server.requests.single()
            assertEquals("/repos/khorum-oss/kontinuance/contents/kontinuance.yml", request.path)
            assertTrue(request.rawUri.contains("ref=abc123"), request.rawUri)
            assertEquals("Bearer t0k3n", request.authorization)
        }
    }

    @Test
    fun `returns null when the file does not exist at that ref`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/contents/.+", status = 404, body = """{"message":"Not Found"}""")
            val client = RestGitHubClient(token = "t0k3n", baseUrl = server.baseUrl)

            assertNull(client.fileAt(repo, "kontinuance.yml", "abc123"))
        }
    }

    @Test
    fun `raises on a non-404 failure rather than reporting an absent file`() = runBlocking {
        FakeGitHubServer().use { server ->
            server.on("GET", "/repos/.+/contents/.+", status = 500, body = """{"message":"boom"}""")
            val client = RestGitHubClient(token = "t0k3n", baseUrl = server.baseUrl)

            val error = assertFailsWith<GitHubApiException> { client.fileAt(repo, "kontinuance.yml", "abc") }
            assertEquals(500, error.statusCode)
        }
    }
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :github:test --tests "*RestGitHubClientIT*"`
Expected: FAIL — compilation error, `fileAt` is not a member of `RestGitHubClient`.

- [ ] **Step 3: Add the interface method**

In `GitHubClient.kt`, add below `branchHead`:

```kotlin
    /**
     * The contents of [path] in [repo] at [ref], or `null` when there is no such file at that ref.
     *
     * Used to read a project's descriptor out of its repository (041). [ref] is a commit SHA at every
     * call site, so the descriptor a run parses and the code it builds come from one commit.
     */
    suspend fun fileAt(repo: RepoRef, path: String, ref: String): String?
```

- [ ] **Step 4: Implement it on `RestGitHubClient`**

In `RestGitHubClient.kt`, add after `branchHead`:

```kotlin
    override suspend fun fileAt(repo: RepoRef, path: String, ref: String): String? {
        val encodedPath = path.split('/').joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        val encodedRef = URLEncoder.encode(ref, StandardCharsets.UTF_8)
        // The raw media type returns file contents verbatim, so no base64 decode step is needed.
        val request = baseRequest("$root/repos/${repo.slug}/contents/$encodedPath?ref=$encodedRef")
            .header("Accept", "application/vnd.github.raw")
            .GET()
            .build()
        val response = send(request)
        if (response.statusCode() == NOT_FOUND) return null
        requireSuccess(response)
        return response.body()
    }
```

Add the imports `java.net.URLEncoder` and `java.nio.charset.StandardCharsets`.

Note: `baseRequest` already sets `Accept: application/vnd.github+json`; the extra `.header("Accept", …)` appends rather than replaces, and GitHub honours the raw type. If the IT shows JSON coming back instead of raw text, change `baseRequest` to take the accept type as a parameter rather than adding a second header.

- [ ] **Step 5: Widen the fake**

In `RecordingGitHubClient.kt`, add the constructor parameter and the override:

```kotlin
class RecordingGitHubClient(
    private val pulls: List<PullRequest> = emptyList(),
    private var failuresBeforeSuccess: Int = 0,
    private val branchHeads: Map<String, String> = emptyMap(),
    private val files: Map<String, String> = emptyMap(),
) : GitHubClient {
```

```kotlin
    override suspend fun fileAt(repo: RepoRef, path: String, ref: String): String? = files[path]
```

- [ ] **Step 6: Run the module's tests and verify they pass**

Run: `./gradlew :github:test`
Expected: PASS — the three new IT cases plus every existing case.

- [ ] **Step 7: Commit**

```bash
git add github/src/main/kotlin/org/khorum/oss/kontinuance/github/client/GitHubClient.kt \
        github/src/main/kotlin/org/khorum/oss/kontinuance/github/client/RestGitHubClient.kt \
        github/src/test/kotlin/org/khorum/oss/kontinuance/github/support/RecordingGitHubClient.kt \
        github/src/test/kotlin/org/khorum/oss/kontinuance/github/client/RestGitHubClientIT.kt
git commit -m "feat(041): fetch a file from a repository at a ref"
```

---

### Task 3: The `GitHubClientProvider` seam

**Files:**
- Create: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/GitHubClientProvider.kt`
- Test: `server/src/test/kotlin/org/khorum/oss/kontinuance/server/domain/project/TokenBackedGitHubClientProviderTest.kt` (create)

**Interfaces:**
- Consumes: `GitHubTokenStore.resolve(envVar: String, env: (String) -> String?): String?` (existing).
- Produces: `fun interface GitHubClientProvider { fun client(): GitHubClient? }` and the Spring `@Component` `TokenBackedGitHubClientProvider`. Task 4 depends on the interface only.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.khorum.oss.kontinuance.server.domain.project

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.server.store.GitHubTokenStore
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TokenBackedGitHubClientProviderTest {

    @Test
    fun `yields no client when no token can be resolved`(@TempDir dir: Path) {
        val tokens = GitHubTokenStore(dir.resolve("token").toString())
        val provider = TokenBackedGitHubClientProvider(tokens, "KONTINUANCE_GITHUB_TOKEN", "https://api.github.com")

        assertNull(provider.client())
    }

    @Test
    fun `yields a client when a token is stored`(@TempDir dir: Path) {
        val file = dir.resolve("token")
        file.writeText("t0k3n")
        val tokens = GitHubTokenStore(file.toString())
        val provider = TokenBackedGitHubClientProvider(tokens, "KONTINUANCE_GITHUB_TOKEN", "https://api.github.com")

        assertNotNull(provider.client())
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :server:test --tests "*TokenBackedGitHubClientProviderTest*"`
Expected: FAIL — `TokenBackedGitHubClientProvider` is unresolved.

- [ ] **Step 3: Implement the seam**

```kotlin
package org.khorum.oss.kontinuance.server.domain.project

import org.khorum.oss.kontinuance.github.client.GitHubClient
import org.khorum.oss.kontinuance.github.client.RestGitHubClient
import org.khorum.oss.kontinuance.server.store.GitHubTokenStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Produces a [GitHubClient] authenticated with whatever token the deployment has, or `null` when it has
 * none. Kept as a seam so [DescriptorResolver] never touches token plumbing and stays unit-testable
 * without a real client.
 */
fun interface GitHubClientProvider {
    /** An authenticated client, or `null` when no token is resolvable. */
    fun client(): GitHubClient?
}

/**
 * The production [GitHubClientProvider]: resolves the token through 040's [GitHubTokenStore], which
 * prefers an environment variable named by `tokenEnv` over the stored token. The token is never logged
 * and never leaves this class.
 */
@Component
class TokenBackedGitHubClientProvider(
    private val tokens: GitHubTokenStore,
    @Value("\${kontinuance.github.token-env:KONTINUANCE_GITHUB_TOKEN}") private val tokenEnv: String,
    @Value("\${kontinuance.github.base-url:https://api.github.com}") private val baseUrl: String,
) : GitHubClientProvider {

    override fun client(): GitHubClient? =
        tokens.resolve(tokenEnv)?.let { RestGitHubClient(token = it, baseUrl = baseUrl) }
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew :server:test --tests "*TokenBackedGitHubClientProviderTest*"`
Expected: PASS — 2 tests.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/GitHubClientProvider.kt \
        server/src/test/kotlin/org/khorum/oss/kontinuance/server/domain/project/TokenBackedGitHubClientProviderTest.kt
git commit -m "feat(041): add a token-backed GitHub client provider seam"
```

---

### Task 4: `DescriptorResolver` — the resolution rule

This is the heart of the feature. It implements FR-003, FR-005 and FR-009a.

**Files:**
- Create: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/DescriptorResolver.kt`
- Test: `server/src/test/kotlin/org/khorum/oss/kontinuance/server/domain/project/DescriptorResolverTest.kt` (create)

**Interfaces:**
- Consumes: `RepoRef.parse` (Task 1), `GitHubClient.fileAt` (Task 2), `GitHubClientProvider` (Task 3), and the existing `ProjectStore.activeName/get/source`.
- Produces:
  - `sealed interface Resolution`
  - `data class Resolved(val pipeline: Pipeline, val sha: String?, val origin: Origin) : Resolution`
  - `data class Rejected(val reason: String) : Resolution`
  - `enum class Origin { Stored, Repo, Live }`
  - `suspend fun DescriptorResolver.resolve(): Resolution`

  Task 5 (`RunTrigger`) and Task 7 (`ConfigController`) both consume exactly these names.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.khorum.oss.kontinuance.server.domain.project

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.client.GitHubClient
import org.khorum.oss.kontinuance.github.support.RecordingGitHubClient
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DescriptorResolverTest {

    private val descriptorText = """
        pipeline:
          name: "demo"
          stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]
    """.trimIndent()

    private fun resolverFor(
        dir: Path,
        client: GitHubClient? = null,
        live: Path = dir.resolve("live.yml"),
    ) = DescriptorResolver(
        projects = ProjectStore(dir.resolve("projects")),
        liveDescriptor = live,
        descriptorPath = "kontinuance.yml",
        clients = GitHubClientProvider { client },
    )

    @Test
    fun `a stored descriptor wins and never touches GitHub`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.save("spektr", descriptorText)
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        // A client that would fail if called at all.
        val client = RecordingGitHubClient()

        val result = resolverFor(dir, client).resolve()

        val resolved = assertIs<Resolved>(result)
        assertEquals("demo", resolved.pipeline.name)
        assertEquals(Origin.Stored, resolved.origin)
        assertEquals(null, resolved.sha)
    }

    @Test
    fun `a repo-hosted project resolves branch to sha and fetches the descriptor there`(
        @TempDir dir: Path,
    ) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(
            branchHeads = mapOf("main" to "abc123"),
            files = mapOf("kontinuance.yml" to descriptorText),
        )

        val result = resolverFor(dir, client).resolve()

        val resolved = assertIs<Resolved>(result)
        assertEquals("demo", resolved.pipeline.name)
        assertEquals(Origin.Repo, resolved.origin)
        assertEquals("abc123", resolved.sha)
    }

    @Test
    fun `falls back to the live descriptor when no project is active`(@TempDir dir: Path) = runTest {
        val live = dir.resolve("live.yml")
        live.writeText(descriptorText)

        val result = resolverFor(dir, client = null, live = live).resolve()

        val resolved = assertIs<Resolved>(result)
        assertEquals(Origin.Live, resolved.origin)
    }

    @Test
    fun `rejects a source that is not a GitHub repository`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://gitlab.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")

        val result = resolverFor(dir, RecordingGitHubClient()).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("GitHub"), result.toString())
    }

    @Test
    fun `rejects when no token is available`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")

        val result = resolverFor(dir, client = null).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("token"), result.toString())
    }

    @Test
    fun `rejects an unknown branch`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "nope"))
        projects.setActive("spektr")

        val result = resolverFor(dir, RecordingGitHubClient()).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("nope"), result.toString())
    }

    @Test
    fun `rejects when the repository has no descriptor at that path`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(branchHeads = mapOf("main" to "abc123"))

        val result = resolverFor(dir, client).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("kontinuance.yml"), result.toString())
    }

    @Test
    fun `rejects a descriptor that does not parse, naming its origin`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(
            branchHeads = mapOf("main" to "abc123"),
            // Valid YAML, invalid descriptor: no stages (041 depends on the 040-era guard).
            files = mapOf("kontinuance.yml" to "pipeline:\n  name: \"broken\"\n"),
        )

        val result = resolverFor(dir, client).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("stage"), result.toString())
    }

    @Test
    fun `rejects rather than raising when the API fails`(@TempDir dir: Path) = runTest {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val failing = object : GitHubClient by RecordingGitHubClient() {
            override suspend fun branchHead(
                repo: org.khorum.oss.kontinuance.github.client.RepoRef,
                branch: String,
            ): String? = throw GitHubApiException(500, "boom")
        }

        val result = resolverFor(dir, failing).resolve()

        assertTrue(assertIs<Rejected>(result).reason.contains("500"), result.toString())
    }
}
```

Add `import kotlin.test.assertIs` to the test file.

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :server:test --tests "*DescriptorResolverTest*"`
Expected: FAIL — `DescriptorResolver`, `Resolved`, `Rejected` and `Origin` are unresolved.

- [ ] **Step 3: Implement the resolver**

```kotlin
package org.khorum.oss.kontinuance.server.domain.project

import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** Where a resolved descriptor came from. */
enum class Origin { Stored, Repo, Live }

/** The outcome of asking "what pipeline does this project run?" (041, FR-003). */
sealed interface Resolution

/**
 * A parsed pipeline plus, for a repo-hosted descriptor, the commit [sha] it was read from — which the
 * caller pins the checkout to so descriptor and code always come from one commit (FR-004).
 */
data class Resolved(val pipeline: Pipeline, val sha: String?, val origin: Origin) : Resolution

/** Resolution failed. [reason] is operator-facing and names the cause (FR-005). */
data class Rejected(val reason: String) : Resolution

/**
 * Answers "what pipeline does the active project run?" for the manual trigger path.
 *
 * Order (FR-009a): the active project's stored descriptor, else the active project's repository, else
 * the server's live descriptor file, else a rejection. Every failure is a [Rejected] rather than a
 * thrown exception, so [org.khorum.oss.kontinuance.server.service.RunTrigger] can refuse before a run
 * record exists — a stage-less or missing descriptor must never become a run that starts and then
 * discovers it has nothing to do.
 */
class DescriptorResolver(
    private val projects: ProjectStore,
    private val liveDescriptor: Path,
    private val descriptorPath: String,
    private val clients: GitHubClientProvider,
) {

    suspend fun resolve(): Resolution {
        val active = projects.activeName()
            ?: return fromLive()

        projects.get(active)?.let { text ->
            return parse(text, sha = null, origin = Origin.Stored)
        }

        val source = projects.source(active) ?: return fromLive()
        return fromRepository(source)
    }

    private suspend fun fromRepository(source: ProjectSource): Resolution {
        val url = source.repo ?: return Rejected("the project has no repository")
        val repo = RepoRef.parse(url)
            ?: return Rejected(
                "repo-hosted descriptors need a GitHub repository ($url is not one) — " +
                    "store a descriptor for this project instead",
            )
        val branch = source.branch?.takeIf { it.isNotBlank() }
            ?: return Rejected("the project's source has no branch — a repo-hosted descriptor needs one")
        val client = clients.client()
            ?: return Rejected("no GitHub token available — connect a source or set the token env var")

        return try {
            val sha = client.branchHead(repo, branch)
                ?: return Rejected("branch '$branch' not found on ${repo.slug}")
            val text = client.fileAt(repo, descriptorPath, sha)
                ?: return Rejected("no $descriptorPath on '$branch' at ${repo.slug}")
            parse(text, sha, Origin.Repo)
        } catch (e: GitHubApiException) {
            Rejected("GitHub unreachable — HTTP ${e.statusCode}")
        }
    }

    private fun fromLive(): Resolution {
        if (!liveDescriptor.isRegularFile()) return Rejected("no pipeline descriptor at $liveDescriptor")
        return parse(liveDescriptor.readText(), sha = null, origin = Origin.Live)
    }

    private fun parse(text: String, sha: String?, origin: Origin): Resolution =
        runCatching { Resolved(PipelineDescriptor.parse(text), sha, origin) }
            .getOrElse { Rejected("invalid descriptor: ${it.message}") }
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew :server:test --tests "*DescriptorResolverTest*"`
Expected: PASS — 9 tests.

- [ ] **Step 5: Register it as a Spring bean**

`DescriptorResolver` is a plain class, so it needs a bean definition beside the existing `projectStore`
one in `server/src/main/kotlin/org/khorum/oss/kontinuance/server/config/ServerConfig.kt`:

```kotlin
    /**
     * Descriptor resolution for the manual trigger path (041). Note the two distinct paths: the live
     * descriptor is a file on this server, while `descriptorPath` is a filename looked up *inside* a
     * project's repository.
     */
    @Bean
    fun descriptorResolver(
        projects: ProjectStore,
        clients: GitHubClientProvider,
        @Value("\${kontinuance.config.descriptor:kontinuance.yml}") liveDescriptorPath: String,
        @Value("\${kontinuance.project.descriptorPath:kontinuance.yml}") repoDescriptorPath: String,
    ): DescriptorResolver = DescriptorResolver(
        projects = projects,
        liveDescriptor = Path.of(liveDescriptorPath),
        descriptorPath = repoDescriptorPath,
        clients = clients,
    )
```

`TokenBackedGitHubClientProvider` from Task 3 is already a `@Component`, so it is injected here by type.

- [ ] **Step 6: Verify the context still starts**

Run: `./gradlew :server:test`
Expected: PASS — a missing or duplicate bean surfaces as a context-load failure in the existing Spring
Boot tests, not as a compile error.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/DescriptorResolver.kt \
        server/src/main/kotlin/org/khorum/oss/kontinuance/server/config/ServerConfig.kt \
        server/src/test/kotlin/org/khorum/oss/kontinuance/server/domain/project/DescriptorResolverTest.kt
git commit -m "feat(041): resolve a project's descriptor from its repository"
```

---

### Task 5: `RunTrigger` resolves through `DescriptorResolver` and pins the SHA

**Files:**
- Modify: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/service/RunTrigger.kt`
- Modify: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/TriggerController.kt:21`
- Test: `server/src/test/kotlin/org/khorum/oss/kontinuance/server/service/RunTriggerTest.kt` (add cases)

**Interfaces:**
- Consumes: `DescriptorResolver.resolve()`, `Resolved`, `Rejected` (Task 4).
- Produces: `suspend fun RunTrigger.trigger(): Result` — the existing `Result.Accepted`/`Result.Rejected` shape is unchanged; only the function becomes `suspend`.

`ProjectSourceInjector` is **not** modified. Passing the resolved SHA as the source's `branch` value makes its existing SHA regex pin `GitStep.sha` (034), which is how FR-004 is satisfied for free.

- [ ] **Step 1: Write the failing test**

Add to `RunTriggerTest.kt`:

```kotlin
    @Test
    fun `records no run when the descriptor cannot be resolved`(@TempDir dir: Path) = runTest {
        val store = InMemoryRunStore()
        // No descriptor file, no active project: resolution must fail before anything is recorded.
        val trigger = triggerFor(store, FakeEngine(), dir.resolve("absent.yml"))

        val result = trigger.trigger()

        assertTrue(result is RunTrigger.Result.Rejected)
        assertTrue(store.recent(10).isEmpty(), "no run should be recorded for an unresolvable descriptor")
    }

    @Test
    fun `pins the checkout to the commit the descriptor was read from`(@TempDir dir: Path) = runTest {
        val store = InMemoryRunStore()
        val engine = CapturingEngine()
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        projects.setActive("spektr")
        val client = RecordingGitHubClient(
            branchHeads = mapOf("main" to "abc123"),
            files = mapOf("kontinuance.yml" to validDescriptor),
        )

        triggerFor(store, engine, dir.resolve("live.yml"), projects, client).trigger()

        val checkout = engine.pipeline!!.stages.first().steps.first().definition as GitStep
        assertEquals("abc123", checkout.sha)
        assertNull(checkout.ref)
    }
```

Add `CapturingEngine` beside the existing `FakeEngine` in this file, and widen `triggerFor`:

```kotlin
    /** Records the pipeline it was handed, so a test can assert what the trigger actually built. */
    private class CapturingEngine : PipelineEngine {
        var pipeline: Pipeline? = null

        override suspend fun run(
            pipeline: Pipeline,
            completedStages: List<StageRun>,
            logSink: LogSink?,
            runId: RunId?,
        ): Run {
            this.pipeline = pipeline
            return Run(runId ?: RunId("engine-generated"), pipeline, PipelineStatus.Success, emptyList())
        }

        override fun statuses(runId: RunId): Flow<StatusEvent> = throw UnsupportedOperationException()
        override suspend fun cancel(runId: RunId): Unit = throw UnsupportedOperationException()
    }

    private fun triggerFor(
        store: InMemoryRunStore,
        engine: PipelineEngine,
        path: Path,
        projects: ProjectStore = ProjectStore(path.resolveSibling("projects")),
        client: GitHubClient? = null,
    ): RunTrigger {
        val launcher = RunLauncher(store, engine, CoroutineScope(Dispatchers.Unconfined), InMemoryRunLogStore())
        val resolver = DescriptorResolver(
            projects = projects,
            liveDescriptor = path,
            descriptorPath = "kontinuance.yml",
            clients = GitHubClientProvider { client },
        )
        return RunTrigger(store, launcher, projects, resolver)
    }
```

The existing call sites pass `(store, engine, path)` positionally and keep working, since the two new parameters are defaulted. Match the `PipelineEngine.run` signature to the one `FakeEngine` already implements in this file — if it has drifted, copy from `FakeEngine` rather than from here.

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :server:test --tests "*RunTriggerTest*"`
Expected: FAIL — `trigger()` is not a suspend function and takes no resolver.

- [ ] **Step 3: Rewrite `RunTrigger`**

Replace the constructor and `trigger()`:

```kotlin
@Component
class RunTrigger(
    private val store: RunStore,
    private val launcher: RunLauncher,
    private val projects: ProjectStore,
    private val resolver: DescriptorResolver,
) {

    suspend fun trigger(): Result {
        val resolution = resolver.resolve()
        val resolved = when (resolution) {
            is Rejected -> return Result.Rejected(resolution.reason)
            is Resolved -> resolution
        }

        // Drive the checkout from the active project's source (033). For a repo-hosted descriptor the
        // resolved commit is passed as the source value, which ProjectSourceInjector's existing SHA rule
        // pins as `sha` (034) — so the descriptor and the code always come from one commit.
        val stored = projects.activeName()?.let { projects.source(it) }
        val source = stored?.let { it.copy(branch = resolved.sha ?: it.branch) }
        val pipeline = ProjectSourceInjector.apply(resolved.pipeline, source)
        val repo = source?.repo?.takeIf { it.isNotBlank() }

        val id = "run-" + UUID.randomUUID().toString().substring(0, ID_LEN)
        val startedAt = Instant.now()
        store.record(
            RunRecord(
                id = id,
                pipeline = pipeline.name,
                status = "Running",
                startedAt = startedAt,
                repo = repo,
                sha = resolved.sha,
                trigger = "manual",
            ),
        )
        launcher.launch(id, pipeline, startedAt, repo = repo)
        return Result.Accepted(id)
    }
```

Remove the now-unused `descriptorPath` parameter, the `descriptor` field and the `Files`/`Path`/`PipelineDescriptor` imports. Add imports for `DescriptorResolver`, `Rejected`, `Resolved`.

- [ ] **Step 4: Make the controller suspend**

In `TriggerController.kt:21`, change `fun trigger()` to `suspend fun trigger()`. WebFlux already supports coroutine handlers — `RunController` uses them throughout.

- [ ] **Step 5: Run the module's tests and verify they pass**

Run: `./gradlew :server:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/org/khorum/oss/kontinuance/server/service/RunTrigger.kt \
        server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/TriggerController.kt \
        server/src/test/kotlin/org/khorum/oss/kontinuance/server/service/RunTriggerTest.kt
git commit -m "feat(041): trigger runs from a repo-hosted descriptor, pinned to its commit"
```

---

### Task 6: Register a project without a descriptor

Implements FR-006 and FR-007.

**Files:**
- Modify: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/ProjectDtos.kt`
- Modify: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectController.kt:110-130`
- Test: `server/src/test/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectCreateTest.kt` (create)

**Interfaces:**
- Consumes: `DescriptorResolver` is *not* used here — add-time checking resolves directly through `GitHubClientProvider` + `RepoRef.parse` for the supplied repo/branch, because the project is not active yet.
- Produces: `data class DescriptorCheck(val ok: Boolean, val pipeline: String? = null, val stages: Int? = null, val message: String? = null)` and `CreatedProject(val name: String, val descriptor: DescriptorCheck? = null)`. Task 9 renders these fields.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.server.domain.project.CreateProjectRequest
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectCreateTest {

    @Test
    fun `creates a project with a repo and no descriptor text`(@TempDir dir: Path) = runTest {
        val controller = controllerFor(dir, branchHeads = mapOf("main" to "abc"), files = mapOf("kontinuance.yml" to VALID))

        val response = controller.create(
            CreateProjectRequest(name = "spektr", repo = "https://github.com/khorum-oss/spektr", branch = "main"),
        )

        assertEquals(200, response.statusCode.value())
        val body = response.body as CreatedProject
        assertEquals(true, body.descriptor?.ok)
        assertEquals("demo", body.descriptor?.pipeline)
        assertEquals(1, body.descriptor?.stages)
    }

    @Test
    fun `creates the project anyway when its descriptor cannot be found, with a warning`(
        @TempDir dir: Path,
    ) = runTest {
        val controller = controllerFor(dir, branchHeads = mapOf("main" to "abc"))

        val response = controller.create(
            CreateProjectRequest(name = "spektr", repo = "https://github.com/khorum-oss/spektr", branch = "main"),
        )

        assertEquals(200, response.statusCode.value())
        val body = response.body as CreatedProject
        assertEquals(false, body.descriptor?.ok)
        assertTrue(body.descriptor?.message!!.contains("kontinuance.yml"))
    }

    @Test
    fun `still rejects a project with neither a descriptor nor a repo`(@TempDir dir: Path) = runTest {
        val response = controllerFor(dir).create(CreateProjectRequest(name = "spektr"))

        assertEquals(400, response.statusCode.value())
    }
}
```

Add the fixture and helper to the same test file:

```kotlin
    private val VALID = """
        pipeline:
          name: "demo"
          stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]
    """.trimIndent()

    private fun controllerFor(
        dir: Path,
        branchHeads: Map<String, String> = emptyMap(),
        files: Map<String, String> = emptyMap(),
    ): ProjectController = ProjectController(
        store = ProjectStore(dir.resolve("projects")),
        runs = InMemoryRunStore(),
        descriptorPath = dir.resolve("live.yml").toString(),
        deriveLimit = 500,
        clients = GitHubClientProvider { RecordingGitHubClient(branchHeads = branchHeads, files = files) },
        descriptorFileName = "kontinuance.yml",
    )
```

Use named arguments as above — `ProjectController` takes two `String` parameters and two `Int`-ish ones, so positional construction is easy to get subtly wrong.

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :server:test --tests "*ProjectCreateTest*"`
Expected: FAIL — `CreatedProject` has no `descriptor`, and `create` rejects a request with no `text`.

- [ ] **Step 3: Update the DTOs**

In `ProjectDtos.kt`:

```kotlin
/** What add-time descriptor checking found (041, FR-007). Advisory: the project is created either way. */
data class DescriptorCheck(
    val ok: Boolean,
    val pipeline: String? = null,
    val stages: Int? = null,
    val message: String? = null,
)

/** `POST /api/projects` response: the created project's name, and what its descriptor check found. */
data class CreatedProject(val name: String, val descriptor: DescriptorCheck? = null)
```

Also update `ProjectDto`'s KDoc: `runnable` is now false only when the project has neither a stored descriptor nor a source.

- [ ] **Step 4: Update `create`**

Replace the `text == null` rejection and the parse block:

```kotlin
        if (name == null) {
            return badRequest("malformed request body — expected {\"name\": …}")
        }
        if (text == null && request.repo.isNullOrBlank()) {
            return badRequest("a project needs a descriptor or a repository to read one from")
        }
        if (!ProjectStore.isValidName(name)) {
            return badRequest("invalid project name (use letters, digits, and . _ -)")
        }
        if (store.exists(name)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("project already exists: $name"))
        }
        // A supplied descriptor must still parse before it is stored (032). A repo-hosted one is checked
        // but never blocking: the operator may be registering the project before the file exists (FR-007).
        if (text != null) {
            runCatching { PipelineDescriptor.parse(text) }
                .getOrElse { return badRequest(it.message ?: "invalid descriptor") }
        }
        val check = if (text == null) checkRepository(request.repo!!, request.branch) else null
        withContext(Dispatchers.IO) {
            if (text != null) store.save(name, text)
            store.saveSource(name, ProjectSource(request.repo, request.branch))
        }
        return ResponseEntity.ok(CreatedProject(name, check))
```

And add the checker:

```kotlin
    /** Resolves a would-be repo-hosted descriptor to report what was found. Never throws. */
    private suspend fun checkRepository(repo: String, branch: String?): DescriptorCheck {
        val ref = RepoRef.parse(repo)
            ?: return DescriptorCheck(false, message = "$repo is not a GitHub repository — paste a descriptor instead")
        val target = branch?.takeIf { it.isNotBlank() }
            ?: return DescriptorCheck(false, message = "a repo-hosted descriptor needs a branch")
        val client = clients.client()
            ?: return DescriptorCheck(false, message = "no GitHub token available — the project was created, but runs will fail until one is set")
        return try {
            val sha = client.branchHead(ref, target)
                ?: return DescriptorCheck(false, message = "branch '$target' not found on ${ref.slug}")
            val text = client.fileAt(ref, descriptorFileName, sha)
                ?: return DescriptorCheck(false, message = "no $descriptorFileName on '$target' at ${ref.slug}")
            val pipeline = PipelineDescriptor.parse(text)
            DescriptorCheck(true, pipeline = pipeline.name, stages = pipeline.stages.size)
        } catch (e: GitHubApiException) {
            DescriptorCheck(false, message = "GitHub unreachable — HTTP ${e.statusCode}")
        } catch (e: DescriptorException) {
            DescriptorCheck(false, message = e.message)
        }
    }
```

Add constructor parameters `private val clients: GitHubClientProvider` and `@Value("\${kontinuance.project.descriptorPath:kontinuance.yml}") private val descriptorFileName: String`, plus the imports.

- [ ] **Step 5: Make a project with a source runnable**

In `list()`, change the `runnable` computation (FR-006):

```kotlin
                    // Runnable when there is something to run: a stored descriptor, or a source to read
                    // one from (041). A derived project with neither stays non-runnable.
                    runnable = name in registered || store.source(name) != null,
```

- [ ] **Step 6: Run the module's tests and verify they pass**

Run: `./gradlew :server:test`
Expected: PASS. If `ProjectControllerDerivedTest` fails on `runnable`, update its expectation — the meaning changed deliberately.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/ProjectDtos.kt \
        server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectController.kt \
        server/src/test/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectCreateTest.kt
git commit -m "feat(041): register a project from a repository alone"
```

---

### Task 7: Config reports origin and supports reverting an override

Implements FR-008.

**Files:**
- Modify: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/ConfigDtos.kt:7`
- Modify: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/config/ConfigController.kt`
- Test: `server/src/test/kotlin/org/khorum/oss/kontinuance/server/controller/config/ConfigOriginTest.kt` (create)

**Interfaces:**
- Consumes: `DescriptorResolver`, `Origin` (Task 4).
- Produces: `ConfigResponse(source, text, plan, origin: String, overridden: Boolean)` where `origin` is the lowercased `Origin` name; and `DELETE /api/config/override` returning `200` with the reverted `ConfigResponse`, or `409` when the active project is not overriding anything.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.khorum.oss.kontinuance.server.controller.config

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigOriginTest {

    @Test
    fun `reports a repo-hosted descriptor as not overridden`(@TempDir dir: Path) = runTest {
        val response = controllerFor(dir, repoHosted = true).config()

        assertEquals("repo", response.origin)
        assertFalse(response.overridden)
    }

    @Test
    fun `saving an edit for a repo-hosted project creates a visible override`(@TempDir dir: Path) = runTest {
        val controller = controllerFor(dir, repoHosted = true)

        controller.update(ConfigUpdateRequest(text = VALID))

        val after = controller.config()
        assertEquals("stored", after.origin)
        assertTrue(after.overridden)
    }

    @Test
    fun `reverting removes the override and returns to the repository's descriptor`(
        @TempDir dir: Path,
    ) = runTest {
        val controller = controllerFor(dir, repoHosted = true)
        controller.update(ConfigUpdateRequest(text = VALID))

        val response = controller.revert()

        assertEquals(200, response.statusCode.value())
        assertEquals("repo", controller.config().origin)
    }

    @Test
    fun `reverting a project that is not overriding anything is a conflict`(@TempDir dir: Path) = runTest {
        val controller = controllerFor(dir, repoHosted = true)

        assertEquals(409, controller.revert().statusCode.value())
    }
}
```

Add the fixture and helper to this test file:

```kotlin
    private val VALID = """
        pipeline:
          name: "demo"
          stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]
    """.trimIndent()

    private fun controllerFor(dir: Path, repoHosted: Boolean): ConfigController {
        val projects = ProjectStore(dir.resolve("projects"))
        projects.saveSource("spektr", ProjectSource("https://github.com/khorum-oss/spektr", "main"))
        if (!repoHosted) projects.save("spektr", VALID)
        projects.setActive("spektr")
        val clients = GitHubClientProvider {
            RecordingGitHubClient(
                branchHeads = mapOf("main" to "abc123"),
                files = mapOf("kontinuance.yml" to VALID),
            )
        }
        val resolver = DescriptorResolver(
            projects = projects,
            liveDescriptor = dir.resolve("live.yml"),
            descriptorPath = "kontinuance.yml",
            clients = clients,
        )
        return ConfigController(
            descriptorPath = dir.resolve("live.yml").toString(),
            projects = projects,
            resolver = resolver,
        )
    }
```

`ConfigController`'s existing constructor takes only the descriptor path; Step 4 adds the `projects` and
`resolver` parameters this helper passes.

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :server:test --tests "*ConfigOriginTest*"`
Expected: FAIL — `ConfigResponse` has no `origin`, and `revert` is unresolved.

- [ ] **Step 3: Widen `ConfigResponse`**

```kotlin
/**
 * The resolved descriptor the server would run, with where it came from (041): `stored` (a descriptor
 * saved on this server), `repo` (read from the project's repository), or `live` (the server's descriptor
 * file, used when no project is active). [overridden] is true exactly when a stored descriptor is
 * shadowing a repository that also has one.
 */
data class ConfigResponse(
    val source: String,
    val text: String,
    val plan: PlanSummary,
    val origin: String = "live",
    val overridden: Boolean = false,
)
```

- [ ] **Step 4: Report the origin and add `revert`**

`config()` becomes `suspend`, resolves through `DescriptorResolver`, and reports `origin`/`overridden`. `overridden` is true when the resolution's origin is `Stored` **and** the active project has a source with a parseable GitHub repo. Add:

```kotlin
    @DeleteMapping("/api/config/override")
    suspend fun revert(): ResponseEntity<*> = withContext(Dispatchers.IO) {
        val active = projects.activeName()
            ?: return@withContext conflict("no active project")
        if (projects.get(active) == null) {
            return@withContext conflict("this project is not overriding a repository descriptor")
        }
        if (projects.source(active) == null) {
            return@withContext conflict("this project has no repository to revert to")
        }
        projects.delete(active)
        ResponseEntity.ok(config())
    }
```

Add `ProjectStore.delete(name)` (`resolve(name + SUFFIX).deleteIfExists()`) and a small `conflict(message)` helper returning `409` with an `ErrorResponse`. `update` additionally writes `store.save(activeName, text)` so the edit becomes the project's stored descriptor, not only the live file.

- [ ] **Step 5: Run the module's tests and verify they pass**

Run: `./gradlew :server:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/ConfigDtos.kt \
        server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/config/ConfigController.kt \
        server/src/main/kotlin/org/khorum/oss/kontinuance/server/store/ProjectStore.kt \
        server/src/test/kotlin/org/khorum/oss/kontinuance/server/controller/config/ConfigOriginTest.kt
git commit -m "feat(041): report descriptor origin and allow reverting an override"
```

---

### Task 8: Web types and presentation helpers

**Files:**
- Modify: `web/src/lib/api/types.ts`
- Modify: `web/src/lib/api/present.ts`
- Test: `web/src/lib/api/present.test.ts`

**Interfaces:**
- Produces: `descriptorOriginLabel(config: ConfigResponse): string` and `descriptorCheckMessage(check: DescriptorCheck | undefined): {tone: 'ok'|'warn', text: string} | null`. Tasks 9 and 10 render these.

- [ ] **Step 1: Write the failing test**

Append to `present.test.ts`:

```typescript
describe('descriptorOriginLabel', () => {
	it('names where the descriptor came from', () => {
		expect(descriptorOriginLabel({ origin: 'repo', overridden: false })).toBe('from the repository');
		expect(descriptorOriginLabel({ origin: 'live', overridden: false })).toBe(
			"from this server's descriptor file"
		);
	});

	it('calls out an override explicitly', () => {
		expect(descriptorOriginLabel({ origin: 'stored', overridden: true })).toBe(
			'overriding the repository'
		);
	});
});

describe('descriptorCheckMessage', () => {
	it('summarises a descriptor that was found', () => {
		expect(descriptorCheckMessage({ ok: true, pipeline: 'spektr-ci', stages: 3 })).toEqual({
			tone: 'ok',
			text: "found kontinuance.yml — pipeline 'spektr-ci', 3 stages"
		});
	});

	it('passes a failure through as a warning', () => {
		expect(descriptorCheckMessage({ ok: false, message: 'no kontinuance.yml on main' })).toEqual({
			tone: 'warn',
			text: 'no kontinuance.yml on main'
		});
	});

	it('is null when nothing was checked', () => {
		expect(descriptorCheckMessage(undefined)).toBeNull();
	});
});
```

Import both functions at the top of the file.

- [ ] **Step 2: Run the test and verify it fails**

Run: `pnpm -C web test`
Expected: FAIL — `descriptorOriginLabel is not a function`.

- [ ] **Step 3: Add the types**

In `types.ts`:

```typescript
/** What add-time descriptor checking found (041). Advisory — the project is created either way. */
export interface DescriptorCheck {
	ok: boolean;
	pipeline?: string;
	stages?: number;
	message?: string;
}

export interface CreatedProject {
	name: string;
	descriptor?: DescriptorCheck;
}
```

and add `origin?: string; overridden?: boolean;` to the existing `ConfigResponse`.

- [ ] **Step 4: Implement the helpers**

In `present.ts`:

```typescript
// ----- descriptor provenance (041) -----

/** Where the descriptor the server would run came from, in words. Pure. */
export function descriptorOriginLabel(config: { origin?: string; overridden?: boolean }): string {
	if (config.overridden) return 'overriding the repository';
	switch (config.origin) {
		case 'repo':
			return 'from the repository';
		case 'stored':
			return 'stored on this server';
		default:
			return "from this server's descriptor file";
	}
}

/** The add-time descriptor check as a tone + line, or null when nothing was checked. Pure. */
export function descriptorCheckMessage(
	check: DescriptorCheck | undefined
): { tone: 'ok' | 'warn'; text: string } | null {
	if (!check) return null;
	if (check.ok) {
		const stages = check.stages ?? 0;
		return {
			tone: 'ok',
			text: `found kontinuance.yml — pipeline '${check.pipeline}', ${stages} stage${stages === 1 ? '' : 's'}`
		};
	}
	return { tone: 'warn', text: check.message ?? 'the descriptor could not be read' };
}
```

- [ ] **Step 5: Run the tests and typecheck**

Run: `pnpm -C web test && pnpm -C web exec svelte-check`
Expected: PASS, 0 errors.

- [ ] **Step 6: Commit**

```bash
git add web/src/lib/api/types.ts web/src/lib/api/present.ts web/src/lib/api/present.test.ts
git commit -m "feat(041): present descriptor origin and add-time check results"
```

---

### Task 9: Reduce the Add Project form

**Files:**
- Create: `web/src/lib/components/AddProject.svelte`
- Modify: `web/src/lib/components/Login.svelte:205-270`
- Modify: `web/src/lib/api/client.ts` (the create-project call returns `CreatedProject`)

`Login.svelte` is 809 lines and already carries the entry shell, the picker and this form. Extracting the form is part of this task, not a separate refactor: the file is being modified anyway and would otherwise grow.

**Interfaces:**
- Consumes: `descriptorCheckMessage` (Task 8), `CreatedProject` (Task 8), `POST /api/projects` (Task 6).
- Produces: `<AddProject onadded={(name: string) => void} />`.

- [ ] **Step 1: Extract the existing form unchanged**

Move the markup currently at `Login.svelte:205-270` into `AddProject.svelte` with props `{ onadded }`, keeping every existing `aria-label` (`new project repo`, `new project branch`, `descriptor source`) so the current e2e keeps passing. Render `<AddProject onadded={…} />` in its place.

- [ ] **Step 2: Run the e2e to prove the extraction changed nothing**

Run: `pnpm -C web exec playwright test --grep "adds a project"`
Expected: PASS — unchanged behavior.

- [ ] **Step 3: Commit the pure extraction**

```bash
git add web/src/lib/components/AddProject.svelte web/src/lib/components/Login.svelte
git commit -m "refactor(041): extract the add-project form from Login.svelte"
```

- [ ] **Step 4: Write the failing e2e for the reduced form**

In `web/e2e/app.spec.ts`, add to the project-picker describe:

```typescript
	test('connects a project from a repository with no descriptor', async ({ page }) => {
		await mockApi(page);
		await page.goto('/');
		await page.getByPlaceholder('username').fill('mkuraja');
		await page.getByPlaceholder('password').fill('s3cret');
		await page.getByText('SIGN IN', { exact: true }).click();

		await page.getByRole('button', { name: '+ ADD PROJECT', exact: true }).click();
		await page.getByPlaceholder(/project name/).fill('spektr');
		await page.getByLabel('new project repo').fill('https://github.com/khorum-oss/spektr');
		await page.getByLabel('new project branch').fill('main');

		// The descriptor box is not shown until asked for.
		await expect(page.getByLabel('descriptor source')).toBeHidden();

		await page.getByRole('button', { name: 'SAVE PROJECT', exact: true }).click();
		await expect(page.getByText(/found kontinuance\.yml/)).toBeVisible();
		await expect(page.getByText('spektr', { exact: true })).toBeVisible();
	});
```

Extend `mockApi` so `POST /api/projects` returns `{name, descriptor: {ok: true, pipeline: 'spektr-ci', stages: 3}}`.

- [ ] **Step 5: Run it and verify it fails**

Run: `pnpm -C web exec playwright test --grep "connects a project from a repository"`
Expected: FAIL — the descriptor textarea is visible and required.

- [ ] **Step 6: Reduce the form**

In `AddProject.svelte`: default the branch input to `main`; put the descriptor textarea behind a `let showDescriptor = $state(false)` toggle labelled `paste a descriptor instead`; send `text` only when non-empty; render `descriptorCheckMessage(result.descriptor)` after a successful save with `ok` in the ok colour and `warn` in the warn colour.

- [ ] **Step 7: Run the web checks and verify they pass**

Run: `pnpm -C web test && pnpm -C web exec svelte-check && pnpm -C web exec playwright test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add web/src/lib/components/AddProject.svelte web/src/lib/api/client.ts web/e2e/app.spec.ts
git commit -m "feat(041): connect a project with only a repository and branch"
```

---

### Task 10: Config screen provenance, override banner and revert

**Files:**
- Modify: `web/src/lib/screens/Config.svelte`
- Modify: `web/src/lib/api/client.ts` (add `revertConfigOverride()` calling `DELETE /api/config/override`)
- Test: `web/e2e/app.spec.ts` (config screen describe)

**Interfaces:**
- Consumes: `descriptorOriginLabel` (Task 8), `ConfigResponse.origin`/`.overridden` (Task 7).

- [ ] **Step 1: Write the failing e2e**

```typescript
	test('shows where the descriptor came from and reverts an override', async ({ page }) => {
		await mockApi(page);
		await page.goto('/config');

		await expect(page.getByText('from the repository')).toBeVisible();
		await expect(page.getByRole('button', { name: 'REVERT TO REPO', exact: true })).toBeHidden();

		await page.getByRole('textbox').fill('pipeline:\n  name: "patched"\n  stages: [{ name: "s", steps: [{ name: "x", run: "true" }] }]');
		await page.getByRole('button', { name: 'SAVE', exact: true }).click();

		await expect(page.getByText('OVERRIDDEN')).toBeVisible();
		await page.getByRole('button', { name: 'REVERT TO REPO', exact: true }).click();
		await expect(page.getByText('from the repository')).toBeVisible();
	});
```

Extend `mockApi` so `GET /api/config` returns `origin: 'repo', overridden: false`, flipping to `origin: 'stored', overridden: true` after a `POST`, and back on `DELETE /api/config/override`.

- [ ] **Step 2: Run it and verify it fails**

Run: `pnpm -C web exec playwright test --grep "shows where the descriptor came from"`
Expected: FAIL — no provenance line is rendered.

- [ ] **Step 3: Render provenance, the banner and the revert action**

In `Config.svelte`: show `descriptorOriginLabel(config)` beside the source line; when `config.overridden`, render an amber banner reading `OVERRIDDEN — this server is running a stored descriptor, not the repository's` with a `REVERT TO REPO` button calling the new client function and refetching. Reuse the amber tokens the approval gate in `RunDetail.svelte` already uses (`--k-warn`).

- [ ] **Step 4: Run the web checks and verify they pass**

Run: `pnpm -C web test && pnpm -C web exec svelte-check && pnpm -C web exec playwright test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/lib/screens/Config.svelte web/src/lib/api/client.ts web/e2e/app.spec.ts
git commit -m "feat(041): show descriptor provenance and revert an override"
```

---

### Task 11: Update the 039 trigger-enablement expectation

FR-006 reverses a rule 039 asserted. That test is correct today and wrong after Task 6; it must be updated deliberately rather than deleted.

**Files:**
- Modify: `web/e2e/project-registry.spec.ts:63` (`disables the trigger for a project with no descriptor and explains why`)

- [ ] **Step 1: Run the suite and confirm exactly which cases fail**

Run: `pnpm -C web exec playwright test project-registry.spec.ts`
Expected: the no-descriptor case fails, because such a project is now runnable when it has a source.

- [ ] **Step 2: Split the case in two**

Keep the existing assertion for a project with **neither** a descriptor nor a source (still disabled, still explains why). Add a sibling asserting that a project with a source and no descriptor **is** enabled, and adjust the fixture so each project exercises one shape.

- [ ] **Step 3: Run the suite and verify it passes**

Run: `pnpm -C web exec playwright test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add web/e2e/project-registry.spec.ts
git commit -m "test(041): a project with a source but no descriptor is runnable"
```

---

### Task 12: Full verification and documentation

**Files:**
- Modify: `docs/roadmap.md` (the "Current state" narrative)
- Modify: `specs/041-repo-hosted-descriptor/spec.md` (status line)

- [ ] **Step 1: Run every check**

```bash
./gradlew check
pnpm -C web test
pnpm -C web exec svelte-check
pnpm -C web exec playwright test
```

Expected: all green. `./gradlew check` includes detekt and kover verification — a new file that trips a detekt rule fails the build.

- [ ] **Step 2: Update the roadmap**

Append to the "Current state" prose, in the established voice, a clause describing 041: connecting a project by repository alone, the descriptor read from `kontinuance.yml` at the resolved commit, the checkout pinned to that same commit, and a stored descriptor still winning as an explicit override. Note in "Remaining" that the event-source path still resolves descriptors from local files.

- [ ] **Step 3: Flip the spec status**

Change the status line in `specs/041-repo-hosted-descriptor/spec.md` from `Designed, unbuilt` to `Built`.

- [ ] **Step 4: Commit**

```bash
git add docs/roadmap.md specs/041-repo-hosted-descriptor/spec.md
git commit -m "docs(041): record repo-hosted descriptors in the roadmap"
```

---

## Notes for the implementer

**The SHA-pinning trick (Task 5) is the subtle part.** `ProjectSourceInjector` already treats a source value matching `[0-9a-fA-F]{7,40}` as a commit to pin rather than a branch to check out (034). Passing the resolved SHA through as the source's `branch` value is therefore all that FR-004 requires — resist the urge to add a `sha` field to `ProjectSource` or to modify the injector. If a test suggests otherwise, re-read `ProjectSourceInjector.sourceCheckout`.

**Resolution must stay failure-tolerant.** Every branch of `DescriptorResolver.resolve` returns a `Resolution`; nothing escapes as an exception. This is what preserves the guarantee that a bad descriptor never produces a run record. If you find yourself adding a `throw`, you are breaking FR-005.

**Do not wire the event source to the resolver.** `EventSource`/`TriggerResolver` keep loading descriptors from local paths. That is explicitly out of scope in the spec, and changing it would alter `TriggerResolver`'s contract for the poller.
