# Project Auto-Registry & Session-Gated Dashboard — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a project appear in the Kontinuance dashboard on its own, derived from the runs the server already reads, so a repository's builds are visible without anyone registering it by hand.

**Architecture:** An optional `project:` key on the descriptor flows into `Pipeline`, gets stamped onto every `RunRecord` at record time, and is read back by a single shared resolver (explicit name → repository short name → none). `GET /api/projects` folds those resolved names over the run store and merges them with the registered descriptors, computing derived entries on every read rather than persisting them — so the project store keeps its invariant that a file in it is a real descriptor. The web dashboard scopes its runs list to the active project using 037's existing client-side projection, and disables the trigger for a project with no descriptor.

**Tech Stack:** Kotlin 2.3.21, Spring Boot 4.1 (WebFlux + coroutines), JUnit 5 + `kotlin.test`, `snakeyaml-engine` descriptor parser, Konstellation meta-DSL (KSP-generated builders), SvelteKit 5 (runes) + Vitest + Playwright.

**Spec:** [`spec.md`](spec.md)

## Global Constraints

- **No new runtime dependency.** Dependency verification (`gradle/verification-metadata.xml`) stays enabled; nothing in this feature may require adding a trusted artifact. (FR-011)
- **Strict descriptor parsing is preserved.** Adding `project` to the allowed key set must not loosen rejection of any other unknown top-level key. (FR-001)
- **Derived projects are never written to disk.** They are recomputed on each listing. (FR-004)
- **Resolution is one shared rule**, used everywhere a project is inferred — never re-implemented inline. (FR-003)
- **Safe-slug rule**: a project name matches `[A-Za-z0-9._-]{1,64}` (`ProjectStore.isValidName`). A resolved name failing it resolves to none; it is never sanitized. (FR-003)
- **No authentication code is written.** Enforcement ships in 016; enabling it is deployment work in Task 10. (FR-010)
- Existing suites stay green: `./gradlew check` for JVM modules, `pnpm -C web test` for the web unit tests, `pnpm -C web exec playwright test` for the e2e suite.
- **No new web dependency in particular:** there is no `@testing-library/svelte` here. Rendered behavior is asserted in Playwright against `web/e2e/mock.ts`; pure logic in a vitest `.test.ts`.

---

## File Structure

**kontinuance (this repo)**

| File | Responsibility | Change |
|---|---|---|
| `engine/src/main/kotlin/.../engine/model/Pipeline.kt` | The shared pipeline model both front-ends produce | Add optional `project` |
| `engine/src/main/kotlin/.../engine/descriptor/PipelineDescriptor.kt` | Strict YAML → `Pipeline` parser | Allow + read `project` |
| `persistence/src/main/kotlin/.../persistence/RunRecord.kt` | The persisted run summary and its JSON codec | Add `project`, stamp it in `from`, round-trip it |
| `server/src/main/kotlin/.../server/domain/project/ProjectResolver.kt` | **New.** The single "which project is this run?" rule | Create |
| `server/src/main/kotlin/.../server/domain/project/ProjectDtos.kt` | `/api/projects` wire shapes | Add derived/runnable/stats fields |
| `server/src/main/kotlin/.../server/controller/ProjectController.kt` | Lists, creates, activates projects | Fold in derived entries; activate without a descriptor |
| `web/src/lib/api/types.ts` | TypeScript mirrors of the wire shapes | Add the new `Project` fields |
| `web/src/lib/api/present.ts` | Pure run-list projection (037 filters) | Add a `project` criterion |
| `web/src/lib/components/Login.svelte` | The entry shell: sign-in + project picker | Badge derived projects, show stats |
| `web/src/routes/+layout.svelte` | App shell; owns which project is active | Track the selected project |
| `web/src/routes/+page.svelte` | Runs list screen; owns filter state | Add project scoping + "all projects" |
| `web/src/lib/screens/Runs.svelte` | Runs table + RUN PIPELINE control | Disable trigger with a reason |

**hestia-systems (sibling repo, Task 10)** — `platform/deploy/pipelines/relikquary-pr.yaml`, `platform/deploy/scripts/render-kontinuance.sh`, `ops/runbooks/kontinuance-deploy-stage.md`, `ops/pipeline-readiness.md`.

---

## Task 1: `project` on the pipeline model and descriptor

**Files:**
- Modify: `engine/src/main/kotlin/org/khorum/oss/kontinuance/engine/model/Pipeline.kt`
- Modify: `engine/src/main/kotlin/org/khorum/oss/kontinuance/engine/descriptor/PipelineDescriptor.kt:37` (`PIPELINE_KEYS`) and `:56-73` (`parse`)
- Test: `engine/src/test/kotlin/org/khorum/oss/kontinuance/engine/descriptor/PipelineDescriptorTest.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `Pipeline.project: String?` — read by Task 2 as `run.pipeline.project`.

> `project` is added as the **last** constructor parameter so every existing positional call —
> including `Pipeline(name, stages, concurrency)` in the parser — keeps compiling unchanged. The
> Kotlin DSL builder is generated from the data class by Konstellation KSP, so no builder code is
> hand-written; a nullable field with a default needs no annotation (same as `Step.timeout`).

- [ ] **Step 1: Write the failing tests**

In `PipelineDescriptorTest.kt`:

```kotlin
    @Test
    fun `parses the optional project name`() {
        val yaml = """
            pipeline:
              name: "relikquary-pr"
              project: "relikquary"
              stages: []
        """.trimIndent()

        assertEquals("relikquary", PipelineDescriptor.parse(yaml).project)
    }

    @Test
    fun `project is null when the key is absent`() {
        val yaml = """
            pipeline:
              name: "relikquary-pr"
              stages: []
        """.trimIndent()

        assertEquals(null, PipelineDescriptor.parse(yaml).project)
    }

    @Test
    fun `still rejects an unknown top-level pipeline key`() {
        val yaml = """
            pipeline:
              name: "relikquary-pr"
              projekt: "relikquary"
              stages: []
        """.trimIndent()

        val error = assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
        assertTrue(error.message!!.contains("projekt"))
    }

    @Test
    fun `rejects a blank project name`() {
        val yaml = """
            pipeline:
              name: "relikquary-pr"
              project: "  "
              stages: []
        """.trimIndent()

        assertFailsWith<DescriptorException> { PipelineDescriptor.parse(yaml) }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :engine:test --tests '*PipelineDescriptorTest*'`
Expected: FAIL — `parses the optional project name` fails because `project` is an unknown key (and `Pipeline` has no such property, so it will not compile until Step 3).

- [ ] **Step 3: Add the field to the model**

In `Pipeline.kt`, add the parameter last and validate it:

```kotlin
@GeneratedDsl
@RootDsl(name = "pipeline", alias = "")
data class Pipeline(
    val name: String,
    @ListDsl
    @DefaultEmptyList
    val stages: List<Stage> = emptyList(),
    @DefaultValue("1")
    val concurrency: Int = 1,
    val project: String? = null,
) {
    init {
        require(name.isNotBlank()) { "pipeline name must be non-empty" }
        require(concurrency >= 1) { "pipeline '$name' concurrency must be >= 1, was $concurrency" }
        val duplicates = stages.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate stage names in pipeline '$name': $duplicates" }
        project?.let {
            require(it.isNotBlank()) { "pipeline '$name' project must be non-blank when set" }
        }
    }
}
```

Also extend its KDoc:

```kotlin
 * @param project optional owning project name; groups several pipelines (a PR gate, a delivery
 *   pipeline, a promotion) under one project in the dashboard. When `null` the project is inferred
 *   from the run's repository. Must be non-blank when present.
```

- [ ] **Step 4: Allow and read the key in the parser**

In `PipelineDescriptor.kt`, extend the allowed keys:

```kotlin
    private val PIPELINE_KEYS = setOf("name", "concurrency", "stages", "project")
```

and read it in `parse`, passing it to the constructor:

```kotlin
        val name = asString(requireKey(pipelineMap, "name", "pipeline"), "pipeline.name")
        val concurrency = pipelineMap["concurrency"]?.let { asInt(it, "pipeline.concurrency") } ?: 1
        val project = pipelineMap["project"]?.let { asString(it, "pipeline.project") }
        val stages = asListOrEmpty(pipelineMap["stages"], "pipeline.stages")
            .mapIndexed { i, raw -> parseStage(raw, "pipeline.stages[$i]") }

        return construct("pipeline") { Pipeline(name, stages, concurrency, project) }
```

`construct` already converts an `IllegalArgumentException` from the `init` block into a `DescriptorException`, which is what makes the blank-project test pass.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :engine:test --tests '*PipelineDescriptorTest*'`
Expected: PASS (4 new tests green, all pre-existing tests still green).

- [ ] **Step 6: Verify the generated DSL still builds**

Run: `./gradlew :engine:build`
Expected: BUILD SUCCESSFUL — KSP regenerates the `pipeline { }` builder with the new optional field.

- [ ] **Step 7: Commit**

```bash
git add engine/src/main/kotlin/org/khorum/oss/kontinuance/engine/model/Pipeline.kt \
        engine/src/main/kotlin/org/khorum/oss/kontinuance/engine/descriptor/PipelineDescriptor.kt \
        engine/src/test/kotlin/org/khorum/oss/kontinuance/engine/descriptor/PipelineDescriptorTest.kt
git commit -m "feat(039): optional project name on the pipeline descriptor"
```

---

## Task 2: Stamp the project onto every run record

**Files:**
- Modify: `persistence/src/main/kotlin/org/khorum/oss/kontinuance/persistence/RunRecord.kt`
- Test: `persistence/src/test/kotlin/org/khorum/oss/kontinuance/persistence/RunRecordTest.kt`

**Interfaces:**
- Consumes: `Pipeline.project` (Task 1).
- Produces: `RunRecord.project: String?`, serialized as `"project"`, absent when null. Read by Task 3's resolver and served verbatim on `/api/runs` (the server returns `RunsResponse(runs: List<RunRecord>)`, so the field reaches the browser with no DTO change).

> ⚠️ This file's test suite is also touched by the stashed WIP (`stash@{0}` — the `RunBuilder`
> scaffolding). If that stash is popped, reconcile `RunRecordTest.kt` by hand.

- [ ] **Step 1: Write the failing tests**

In `RunRecordTest.kt`:

```kotlin
    @Test
    fun `stamps the pipeline's project onto the record`() {
        val run = Run(
            id = RunId("r1"),
            pipeline = Pipeline(name = "relikquary-pr", project = "relikquary"),
            status = PipelineStatus.Success,
            stageRuns = emptyList(),
        )

        assertEquals("relikquary", RunRecord.from(run, Instant.parse("2026-08-16T00:00:00Z")).project)
    }

    @Test
    fun `project is null when the pipeline declares none`() {
        val run = Run(
            id = RunId("r2"),
            pipeline = Pipeline(name = "relikquary-pr"),
            status = PipelineStatus.Success,
            stageRuns = emptyList(),
        )

        assertEquals(null, RunRecord.from(run, Instant.parse("2026-08-16T00:00:00Z")).project)
    }

    @Test
    fun `round-trips the project through json`() {
        val record = RunRecord(id = "r3", pipeline = "relikquary-pr", status = "Success", project = "relikquary")

        assertEquals("relikquary", RunRecord.fromJson(record.toJson()).project)
    }

    @Test
    fun `omits the project key when null and reads a legacy record back`() {
        val record = RunRecord(id = "r4", pipeline = "relikquary-pr", status = "Success")
        val json = record.toJson()

        assertTrue(!json.contains("project"))
        assertEquals(null, RunRecord.fromJson(json).project)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :persistence:test --tests '*RunRecordTest*'`
Expected: FAIL — `RunRecord` has no `project` property, so the test source does not compile.

- [ ] **Step 3: Add the field, the codec, and the stamping**

In `RunRecord.kt`, add the property after `trigger` (every construction site uses named arguments):

```kotlin
    val trigger: String? = null,
    val project: String? = null,
    val stages: List<StageRecord> = emptyList(),
```

Extend its KDoc:

```kotlin
 * `project` is the owning project declared by the pipeline (039); when absent the reader infers it
 * from `repo`, so records written before the field existed still resolve to a project.
```

In `toJson()`, beside the other optional puts:

```kotlin
        trigger?.let { put("trigger", it) }
        project?.let { put("project", it) }
```

In `fromJson()`, beside the other reads:

```kotlin
                trigger = str("trigger"),
                project = str("project"),
```

In `from()`, read it off the pipeline so **every** caller (the CI event source and the server's manual trigger alike) stamps it with no signature change:

```kotlin
                trigger = trigger,
                project = run.pipeline.project,
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :persistence:test`
Expected: PASS — the 4 new tests green and the existing persistence suite unchanged.

- [ ] **Step 5: Verify nothing else constructed the record positionally**

Run: `./gradlew :persistence:build :github:build :server:build`
Expected: BUILD SUCCESSFUL. A compile error here means some call site used positional arguments — fix it by naming them rather than reordering the data class.

- [ ] **Step 6: Commit**

```bash
git add persistence/src/main/kotlin/org/khorum/oss/kontinuance/persistence/RunRecord.kt \
        persistence/src/test/kotlin/org/khorum/oss/kontinuance/persistence/RunRecordTest.kt
git commit -m "feat(039): record the owning project on every run"
```

---

## Task 3: The shared project-resolution rule

**Files:**
- Create: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/ProjectResolver.kt`
- Test: `server/src/test/kotlin/org/khorum/oss/kontinuance/server/domain/project/ProjectResolverTest.kt`

**Interfaces:**
- Consumes: `RunRecord.project`, `RunRecord.repo` (Task 2); `ProjectStore.isValidName(String): Boolean`.
- Produces: `ProjectResolver.resolve(record: RunRecord): String?` — the only place a project is inferred. Used by Task 4.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.khorum.oss.kontinuance.server.domain.project

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.persistence.RunRecord
import kotlin.test.assertEquals

class ProjectResolverTest {

    private fun record(project: String? = null, repo: String? = null) =
        RunRecord(id = "r", pipeline = "p", status = "Success", repo = repo, project = project)

    @Test
    fun `prefers the explicit project`() {
        assertEquals("relikquary", ProjectResolver.resolve(record(project = "relikquary", repo = "khorum-oss/other")))
    }

    @Test
    fun `falls back to the repository short name`() {
        assertEquals("relikquary", ProjectResolver.resolve(record(repo = "khorum-oss/relikquary")))
    }

    @Test
    fun `handles a repository with no owner segment`() {
        assertEquals("relikquary", ProjectResolver.resolve(record(repo = "relikquary")))
    }

    @Test
    fun `resolves to none when there is neither project nor repo`() {
        assertEquals(null, ProjectResolver.resolve(record()))
    }

    @Test
    fun `treats a blank project as absent and uses the repo`() {
        assertEquals("relikquary", ProjectResolver.resolve(record(project = "   ", repo = "khorum-oss/relikquary")))
    }

    @Test
    fun `resolves to none when the name is not a safe slug`() {
        assertEquals(null, ProjectResolver.resolve(record(repo = "khorum-oss/reli kquary")))
        assertEquals(null, ProjectResolver.resolve(record(project = "../escape")))
    }

    @Test
    fun `resolves to none for a trailing-slash repo`() {
        assertEquals(null, ProjectResolver.resolve(record(repo = "khorum-oss/")))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :server:test --tests '*ProjectResolverTest*'`
Expected: FAIL — `ProjectResolver` is unresolved.

- [ ] **Step 3: Write the resolver**

```kotlin
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :server:test --tests '*ProjectResolverTest*'`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/ProjectResolver.kt \
        server/src/test/kotlin/org/khorum/oss/kontinuance/server/domain/project/ProjectResolverTest.kt
git commit -m "feat(039): shared project resolution rule"
```

---

## Task 4: Fold derived projects into the listing

**Files:**
- Modify: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/ProjectDtos.kt`
- Modify: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectController.kt`
- Test: `server/src/test/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectControllerDerivedTest.kt` (new)

**Interfaces:**
- Consumes: `ProjectResolver.resolve` (Task 3); `RunStore.recent(limit: Int): List<RunRecord>`.
- Produces: `ProjectDto(name, active, repo?, branch?, derived: Boolean = false, runnable: Boolean = true, runCount: Int = 0, lastStatus: String? = null, lastRunAt: String? = null)` — mirrored in TypeScript by Task 6.

> **Derivation window.** `RunStore` exposes only `recent(limit)`, so derived projects come from the
> most recent N runs. N is `kontinuance.projects.derive-limit`, default **500**. A project whose runs
> have all aged past that window stops being listed — document it, do not hide it.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.khorum.oss.kontinuance.server.controller

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.server.store.ProjectStore
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectControllerDerivedTest {

    private val descriptorText = """
        pipeline:
          name: "demo"
          stages: []
    """.trimIndent()

    private fun controller(dir: Path, runs: InMemoryRunStore): ProjectController {
        val descriptor = dir.resolve("kontinuance.yml")
        descriptor.writeText(descriptorText)
        return ProjectController(ProjectStore(dir.resolve("projects")), runs, descriptor.toString(), 500)
    }

    @Test
    fun `derives a project from runs that was never registered`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "relikquary-pr", status = "Success", repo = "khorum-oss/relikquary"))

        val listed = controller(dir, runs).list().projects.single { it.name == "relikquary" }

        assertTrue(listed.derived)
        assertTrue(!listed.runnable)
        assertEquals(1, listed.runCount)
        assertEquals("Success", listed.lastStatus)
    }

    @Test
    fun `a registered project wins over a derived one of the same name`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "relikquary-pr", status = "Success", project = "relikquary"))
        val subject = controller(dir, runs)
        subject.create(org.khorum.oss.kontinuance.server.domain.project.CreateProjectRequest("relikquary", descriptorText))

        val listed = subject.list().projects.single { it.name == "relikquary" }

        assertTrue(!listed.derived)
        assertTrue(listed.runnable)
        assertEquals(1, listed.runCount)
    }

    @Test
    fun `does not write derived projects to the store`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "relikquary-pr", status = "Success", repo = "khorum-oss/relikquary"))
        val store = ProjectStore(dir.resolve("projects"))
        val descriptor = dir.resolve("kontinuance.yml")
        descriptor.writeText(descriptorText)

        ProjectController(store, runs, descriptor.toString(), 500).list()

        assertTrue(!store.exists("relikquary"))
    }

    @Test
    fun `ignores runs that resolve to no project`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "local", status = "Success"))

        val names = controller(dir, runs).list().projects.map { it.name }

        assertEquals(listOf("default"), names)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :server:test --tests '*ProjectControllerDerivedTest*'`
Expected: FAIL — `ProjectController` has no such constructor and `ProjectDto` has no `derived` property.

- [ ] **Step 3: Extend the DTO**

In `ProjectDtos.kt`:

```kotlin
/**
 * A project on the wire. `derived` marks an entry computed from run history rather than a registered
 * descriptor (039); `runnable` is false exactly when there is no stored descriptor to run. The run
 * statistics come from the derivation window and are `0`/`null` for a project with no recorded runs.
 */
data class ProjectDto(
    val name: String,
    val active: Boolean,
    val repo: String? = null,
    val branch: String? = null,
    val derived: Boolean = false,
    val runnable: Boolean = true,
    val runCount: Int = 0,
    val lastStatus: String? = null,
    val lastRunAt: String? = null,
)
```

- [ ] **Step 4: Fold derived entries into the controller**

In `ProjectController.kt`, take the run store and the window, then merge. Replace the constructor and `list()`:

```kotlin
@RestController
class ProjectController(
    private val store: ProjectStore,
    private val runs: RunStore,
    @Value("\${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
    @Value("\${kontinuance.projects.derive-limit:500}") private val deriveLimit: Int,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    @GetMapping("/api/projects")
    suspend fun list(): ProjectsResponse = withContext(Dispatchers.IO) {
        seedIfEmpty()
        val stats = deriveStats()
        val registered = store.list()
        val names = (registered + stats.keys).distinct().sorted()
        val active = store.activeName()?.takeIf { it in names }
        ProjectsResponse(
            active = active,
            projects = names.map { name ->
                val src = store.source(name)
                val stat = stats[name]
                ProjectDto(
                    name = name,
                    active = name == active,
                    repo = src?.repo,
                    branch = src?.branch,
                    derived = name !in registered,
                    runnable = name in registered,
                    runCount = stat?.count ?: 0,
                    lastStatus = stat?.status,
                    lastRunAt = stat?.at,
                )
            },
        )
    }

    /**
     * Project statistics folded out of the most recent [deriveLimit] runs. Recomputed on every listing
     * — derived projects are a projection over run history, never rows in the project store, so an
     * entry cannot outlive the runs that produced it.
     */
    private fun deriveStats(): Map<String, ProjectStat> {
        val stats = LinkedHashMap<String, ProjectStat>()
        // recent() is newest-first, so the first record seen for a name is its latest run.
        for (record in runs.recent(deriveLimit)) {
            val name = ProjectResolver.resolve(record) ?: continue
            val existing = stats[name]
            stats[name] = if (existing == null) {
                ProjectStat(count = 1, status = record.status, at = record.endedAt?.toString())
            } else {
                existing.copy(count = existing.count + 1)
            }
        }
        return stats
    }

    private data class ProjectStat(val count: Int, val status: String?, val at: String?)
```

Add the imports `org.khorum.oss.kontinuance.persistence.RunStore`, `org.khorum.oss.kontinuance.server.domain.project.ProjectResolver`, and `kotlinx.coroutines.Dispatchers`/`withContext` if not already present.

Note the `active` line: an `.active` name matching neither a registered nor a derived project now reads back as `null`, which is FR-006's "never an error".

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :server:test --tests '*ProjectControllerDerivedTest*'`
Expected: PASS (4 tests).

- [ ] **Step 6: Run the whole server suite**

Run: `./gradlew :server:test`
Expected: PASS — the Spring context wires the new constructor arguments from existing beans (`RunStore` is already a bean in `ServerConfig`). If a `@SpringBootTest` fails on a missing property, confirm the `derive-limit` default is present in the annotation's default value, not only in `application.yml`.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/org/khorum/oss/kontinuance/server/domain/project/ProjectDtos.kt \
        server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectController.kt \
        server/src/test/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectControllerDerivedTest.kt
git commit -m "feat(039): derive projects from run history in the listing"
```

---

## Task 5: Activate a project that has no descriptor

**Files:**
- Modify: `server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectController.kt` (the `activate` handler)
- Test: `server/src/test/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectControllerDerivedTest.kt`

**Interfaces:**
- Consumes: Task 4's `deriveStats` / resolver.
- Produces: `POST /api/projects/{name}/activate` succeeding for a derived name without writing the live descriptor.

- [ ] **Step 1: Write the failing tests**

Append to `ProjectControllerDerivedTest.kt`:

```kotlin
    @Test
    fun `activating a derived project does not overwrite the live descriptor`(@TempDir dir: Path) = runTest {
        val runs = InMemoryRunStore()
        runs.record(RunRecord(id = "r1", pipeline = "relikquary-pr", status = "Success", repo = "khorum-oss/relikquary"))
        val descriptor = dir.resolve("kontinuance.yml")
        descriptor.writeText(descriptorText)
        val subject = ProjectController(ProjectStore(dir.resolve("projects")), runs, descriptor.toString(), 500)

        val response = subject.activate("relikquary")

        assertEquals(200, response.statusCode.value())
        assertEquals(descriptorText, descriptor.readText())
        assertEquals("relikquary", subject.list().active)
    }

    @Test
    fun `activating an unknown project is still rejected`(@TempDir dir: Path) = runTest {
        val subject = controller(dir, InMemoryRunStore())

        assertEquals(404, subject.activate("nope").statusCode.value())
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :server:test --tests '*ProjectControllerDerivedTest*'`
Expected: FAIL — `activating a derived project…` returns 404, because `store.get(name)` is null for a derived project.

- [ ] **Step 3: Make activation descriptor-optional**

Replace the `activate` handler:

```kotlin
    @PostMapping("/api/projects/{name}/activate")
    suspend fun activate(@PathVariable name: String): ResponseEntity<*> = withContext(Dispatchers.IO) {
        val text = store.get(name)
        // A derived project (039) has runs but no stored descriptor: it can be made active — which
        // scopes the dashboard to it — but there is nothing to write as the live descriptor, and
        // overwriting the current one with an unrelated project's pipeline would be a footgun.
        if (text == null && !ProjectStore.isValidName(name)) {
            return@withContext notFound(name)
        }
        if (text == null && name !in deriveStats().keys) {
            return@withContext notFound(name)
        }
        if (text != null) {
            descriptor.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            Files.writeString(descriptor, text)
        }
        store.setActive(name)
        ResponseEntity.ok(ActiveProject(name))
    }

    private fun notFound(name: String): ResponseEntity<*> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("no such project: $name"))
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :server:test`
Expected: PASS — the two new tests green and the existing project-controller tests (registered activation writes the descriptor) unchanged.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectController.kt \
        server/src/test/kotlin/org/khorum/oss/kontinuance/server/controller/ProjectControllerDerivedTest.kt
git commit -m "feat(039): activate a derived project without a descriptor write"
```

---

## Task 6: Web types and the picker's derived badge

**Files:**
- Modify: `web/src/lib/api/types.ts:132-142`
- Modify: `web/src/lib/components/Login.svelte`
- Modify: `web/e2e/mock.ts:275-279` (the `mockProjects` fixture)
- Test: `web/e2e/project-registry.spec.ts` (new)

**Interfaces:**
- Consumes: Task 4's `ProjectDto` shape.
- Produces: `Project` with `derived`, `runnable`, `runCount`, `lastStatus`, `lastRunAt`; the picker emits the selected name through the existing `oncomplete?: (project: string) => void` prop (used by Task 8).

> **Testing model — read before writing any web test.** This repo has **no** `@testing-library/svelte`,
> and adding one would violate the no-new-dependency constraint. `web/package.json` defines two vitest
> projects: `unit` (plain `.test.ts` modules — see `present.test.ts`, `client.test.ts`) and `storybook`
> (renders `*.stories.svelte` as smoke tests). Component *behavior* is asserted in Playwright with the
> shared helpers in `web/e2e/mock.ts` (`mockAuth`, `mockProjects`, `mockApi`, `mockStream`, `enterApp`).
> So: pure logic → a `.test.ts`; rendered behavior → an e2e spec. Never `render(Component, { props })`.
>
> `Login.svelte` takes only `{ requireSignIn, operator, onauthenticated, oncomplete, onsignout }` and
> loads its own project list from `/api/projects` on mount — which is exactly why the fixture, not a
> prop, is where a derived project is introduced.

- [ ] **Step 1: Extend the TypeScript mirror**

In `types.ts`:

```ts
// A named pipeline descriptor the server stores and can run (032). `active` marks the one in effect.
// `repo`/`branch` are the project's optional source (033): when set, a run of the project checks them out.
// `derived` marks a project computed from run history rather than a registered descriptor (039);
// `runnable` is false exactly when there is no descriptor to run.
export interface Project {
	name: string;
	active: boolean;
	repo?: string;
	branch?: string;
	derived?: boolean;
	runnable?: boolean;
	runCount?: number;
	lastStatus?: string;
	lastRunAt?: string;
}
```

- [ ] **Step 2: Add a derived project to the shared e2e fixture**

In `web/e2e/mock.ts`, widen the `mockProjects` fixture type and add a third, derived entry:

```ts
	const projects: {
		name: string;
		active: boolean;
		repo?: string;
		branch?: string;
		derived?: boolean;
		runnable?: boolean;
		runCount?: number;
		lastStatus?: string;
	}[] = [
		{ name: 'kontinuance-service', active: true, runnable: true },
		{ name: 'infra-charts', active: false, runnable: true },
		// A project discovered from run history alone (039): builds, but no registered descriptor.
		{ name: 'relikquary', active: false, derived: true, runnable: false, runCount: 12, lastStatus: 'Success' }
	];
```

- [ ] **Step 3: Write the failing test**

Create `web/e2e/project-registry.spec.ts`:

```ts
import { expect, test } from '@playwright/test';
import { mockApi, mockAuth, mockProjects, mockStream } from './mock';

test.beforeEach(async ({ page }) => {
	await mockAuth(page);
	await mockProjects(page);
	await mockApi(page);
	await mockStream(page);
});

test('badges a derived project and shows its run count in the picker', async ({ page }) => {
	await page.goto('/');
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();

	const row = page.getByText('relikquary', { exact: true }).locator('..');
	await expect(row.getByText('DERIVED')).toBeVisible();
	await expect(row.getByText(/12 runs/)).toBeVisible();
});
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `pnpm -C web exec playwright test project-registry`
Expected: FAIL — no `DERIVED` text is rendered.

- [ ] **Step 5: Render the badge and stats**

In `Login.svelte`, inside the project list item markup, beside the existing `ACTIVE` badge:

```svelte
{#if p.derived}
	<span class="badge badge-derived" title="discovered from run history — no descriptor registered">
		DERIVED
	</span>
{/if}
{#if p.runCount}
	<span class="meta">{p.runCount} runs · last {p.lastStatus ?? '—'}</span>
{/if}
```

and add the styles beside the existing badge rules:

```css
	.badge-derived {
		border-color: var(--muted);
		color: var(--muted);
	}
	.meta {
		color: var(--muted);
		font-size: 0.75rem;
	}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `pnpm -C web exec playwright test project-registry && pnpm -C web test`
Expected: PASS — the new spec green, the existing `app.spec.ts` unaffected by the third fixture entry (if an existing assertion counts project rows, update that count rather than removing the fixture).

- [ ] **Step 7: Commit**

```bash
git add web/src/lib/api/types.ts web/src/lib/components/Login.svelte web/e2e/mock.ts web/e2e/project-registry.spec.ts
git commit -m "feat(039): badge derived projects in the picker"
```

---

## Task 7: Project scoping in the run-list projection

**Files:**
- Modify: `web/src/lib/api/present.ts:84-101`
- Test: `web/src/lib/api/present.test.ts`

**Interfaces:**
- Consumes: `RunRecord.project` (reaching the browser from Task 2).
- Produces: `RunFilter` gains `project: string` (`'all'` or a project name); `matchesRunFilter` applies it using the same precedence as the server.

> The browser must resolve a run's project the same way the server does, or a run will show under a
> project the picker never lists. Mirror the precedence — explicit, then repository short name — and
> keep it in this one pure function.

- [ ] **Step 1: Write the failing test**

In `present.test.ts`:

```ts
describe('matchesRunFilter project scoping', () => {
	const base = { id: 'r1', pipeline: 'relikquary-pr', status: 'Success' };
	const all = { query: '', status: 'all', trigger: 'all', project: 'all' };

	it('matches every run when the project filter is all', () => {
		expect(matchesRunFilter({ ...base, repo: 'khorum-oss/relikquary' }, all)).toBe(true);
	});

	it('matches on the explicit project', () => {
		const r = { ...base, project: 'relikquary', repo: 'khorum-oss/other' };
		expect(matchesRunFilter(r, { ...all, project: 'relikquary' })).toBe(true);
		expect(matchesRunFilter(r, { ...all, project: 'other' })).toBe(false);
	});

	it('falls back to the repository short name', () => {
		const r = { ...base, repo: 'khorum-oss/relikquary' };
		expect(matchesRunFilter(r, { ...all, project: 'relikquary' })).toBe(true);
	});

	it('excludes a run that resolves to no project when one is selected', () => {
		expect(matchesRunFilter(base, { ...all, project: 'relikquary' })).toBe(false);
	});

	it('composes with the status filter', () => {
		const r = { ...base, status: 'Failed', repo: 'khorum-oss/relikquary' };
		expect(matchesRunFilter(r, { ...all, project: 'relikquary', status: 'success' })).toBe(false);
	});
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pnpm -C web test -- present`
Expected: FAIL — `project` is not part of `RunFilter` and is ignored by the matcher.

- [ ] **Step 3: Add the criterion**

In `present.ts`:

```ts
export interface RunFilter {
	query: string;
	status: string; // 'all' | canonical Status
	trigger: string; // 'all' | 'manual' | 'push' | 'pull_request'
	project: string; // 'all' | project name
}

/** The project a run belongs to — the browser mirror of the server's resolution rule (039):
 * the explicit project, else the repository's short name, else none. Pure. */
export function runProject(r: RunRecord): string | null {
	const explicit = r.project?.trim();
	if (explicit) return explicit;
	const fromRepo = r.repo?.split('/').pop()?.trim();
	return fromRepo || null;
}
```

and in `matchesRunFilter`, before the query check:

```ts
	if (f.project !== 'all' && runProject(r) !== f.project) return false;
```

Add `project?: string` to the `RunRecord` interface in `types.ts` if it is not already there.

- [ ] **Step 4: Run the test to verify it passes**

Run: `pnpm -C web test -- present`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/lib/api/present.ts web/src/lib/api/types.ts web/src/lib/api/present.test.ts
git commit -m "feat(039): scope the run-list projection by project"
```

---

## Task 8: Carry the active project through the shell

**Files:**
- Modify: `web/src/routes/+layout.svelte:110`
- Modify: `web/src/routes/+page.svelte:22-35,89-101`
- Modify: `web/src/lib/screens/Runs.svelte:5-42` (the prop block and the filter bar)
- Test: `web/e2e/project-registry.spec.ts` (created in Task 6)

**Interfaces:**
- Consumes: Task 6's `oncomplete?: (project: string) => void`, Task 7's `RunFilter.project` and `runProject`.
- Produces: `Runs.svelte` gains `project?: string`, `projects?: string[]`, and `onproject?: (v: string) => void`, matching the existing `status`/`onstatus` facet convention; `'all'` is the unscoped value.

- [ ] **Step 1: Capture the selected project in the layout**

In `+layout.svelte`, replace the discarding callback:

```svelte
	oncomplete={(name) => {
		activeProject = name;
		view = 'app';
	}}
```

and declare it beside the other shell state, defaulting to every project:

```svelte
	let activeProject = $state('all');
```

Pass it down to the page (follow the existing prop/context pattern this file already uses for shell state).

- [ ] **Step 2: Add the filter state and control on the runs screen**

In `+page.svelte`:

```svelte
	let projectFilter = $state('all');
```

include it in the projection:

```svelte
		runs = filterRuns(all, {
			query,
			status: statusFilter,
			trigger: triggerFilter,
			project: projectFilter
		}).map((r) => toRunView(r));
```

add it to the reactive dependency list beside `statusFilter; triggerFilter;`:

```svelte
	projectFilter;
```

and wire the control alongside the existing facets:

```svelte
	project={projectFilter}
	onproject={(v) => (projectFilter = v)}
```

Initialize `projectFilter` from the layout's `activeProject` so selecting a project in the picker lands on a scoped list, and offer an explicit **ALL PROJECTS** option that sets it back to `'all'`.

- [ ] **Step 3: Add the facet to the runs screen**

In `Runs.svelte`, extend the prop block (both the destructuring and its type) beside the existing facets:

```ts
		trigger = 'all',
		project = 'all',
		projects = [],
```

```ts
		trigger?: string;
		project?: string;
		projects?: string[];
```

```ts
		ontriggerfilter,
		onproject
```

```ts
		ontriggerfilter?: (v: string) => void;
		onproject?: (v: string) => void;
```

Include it in the "is anything filtering?" derivation so the existing reset affordance stays honest:

```ts
	const filtering = $derived(
		query.trim() !== '' || status !== 'all' || trigger !== 'all' || project !== 'all'
	);
```

Render a select in the filter bar next to the status and trigger selects, with an explicit unscoped
option (this is the "ALL PROJECTS" control):

```svelte
<select value={project} onchange={(e) => onproject?.(e.currentTarget.value)}>
	<option value="all">ALL PROJECTS</option>
	{#each projects as p (p)}
		<option value={p}>{p}</option>
	{/each}
</select>
```

- [ ] **Step 4: Write the end-to-end test**

Append to `web/e2e/project-registry.spec.ts` (the fixtures come from `mock.ts`; `sampleRuns` are all
`khorum-oss/kontinuance`, which resolves to project `kontinuance`):

```ts
test('selecting a project scopes the runs list and all projects restores it', async ({ page }) => {
	await page.goto('/');
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();
	await page.getByText('relikquary', { exact: true }).click();

	// sampleRuns belong to `kontinuance`, so scoping to `relikquary` empties the list honestly.
	await expect(page.getByText(/no runs match/i)).toBeVisible();

	await page.getByRole('combobox').filter({ hasText: 'ALL PROJECTS' }).selectOption('all');
	await expect(page.getByText('#KX-2046')).toBeVisible();
});
```

- [ ] **Step 5: Run the tests**

Run: `pnpm -C web test && pnpm -C web exec playwright test project-registry`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add web/src/routes/+layout.svelte web/src/routes/+page.svelte \
        web/src/lib/screens/Runs.svelte web/e2e/project-registry.spec.ts
git commit -m "feat(039): scope the dashboard to the selected project"
```

---

## Task 9: Disable the trigger for a project with no descriptor

**Files:**
- Modify: `web/src/lib/screens/Runs.svelte:5-42` (prop block) and `:57` (the button)
- Modify: `web/src/routes/+page.svelte` (pass `runnable`/`projectName` from the loaded project list)
- Test: `web/e2e/project-registry.spec.ts` (created in Task 6)

**Interfaces:**
- Consumes: `Project.runnable` (Task 6), the active project name (Task 8).
- Produces: nothing downstream — this is the last web task.

- [ ] **Step 1: Write the failing test**

Append to `web/e2e/project-registry.spec.ts` — `relikquary` is the fixture's derived, non-runnable
project, and `kontinuance-service` is registered and runnable:

```ts
test('disables the trigger for a project with no descriptor and explains why', async ({ page }) => {
	await page.goto('/');
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();
	await page.getByText('relikquary', { exact: true }).click();

	await expect(page.getByRole('button', { name: 'RUN PIPELINE' })).toBeDisabled();
	await expect(page.getByText(/no descriptor registered for relikquary/i)).toBeVisible();
});

test('enables the trigger for a registered project', async ({ page }) => {
	await page.goto('/');
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();
	await page.getByText('kontinuance-service', { exact: true }).click();

	await expect(page.getByRole('button', { name: 'RUN PIPELINE' })).toBeEnabled();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pnpm -C web exec playwright test project-registry`
Expected: FAIL — the button ignores `runnable`, so it is enabled for `relikquary`.

- [ ] **Step 3: Honor `runnable` in the control**

In `Runs.svelte`, add to the existing prop block (destructuring **and** its type, beside `triggerError`):

```ts
		runnable = true,
		projectName = '',
```

```ts
		runnable?: boolean;
		projectName?: string;
```

```svelte
<button class="run" disabled={triggering || !runnable} onclick={ontrigger}>
	{triggering ? 'STARTING…' : 'RUN PIPELINE'}
</button>
{#if !runnable}
	<p class="hint">No descriptor registered for {projectName || 'this project'} — add one on the Config screen to run it.</p>
{/if}
```

```css
	.hint {
		color: var(--muted);
		font-size: 0.8rem;
	}
```

Pass `runnable`/`projectName` from `+page.svelte`, reading them off the active project in the loaded project list.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `pnpm -C web test && pnpm -C web exec playwright test`
Expected: PASS — the new specs green and the existing `app.spec.ts` suite still green.

- [ ] **Step 5: Commit**

```bash
git add web/src/lib/screens/Runs.svelte web/src/routes/+page.svelte web/e2e/project-registry.spec.ts
git commit -m "feat(039): disable the trigger for a descriptor-less project"
```

---

## Task 10: Documentation and deployment rollout

**Files:**
- Modify: `docs/running.md` (the `project:` key + the derivation window)
- Modify: `docs/roadmap.md` (039 entry)
- Modify (sibling repo `~/Projects/hestia-systems`): `platform/deploy/pipelines/relikquary-pr.yaml`, `platform/deploy/scripts/render-kontinuance.sh`, `ops/runbooks/kontinuance-deploy-stage.md`, `ops/pipeline-readiness.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the feature observable on the Hestia stage deployment.

- [ ] **Step 1: Document the descriptor key**

In `docs/running.md`, in the descriptor authoring section, add:

```markdown
### `project:` (optional)

Names the project a pipeline belongs to, grouping several pipelines under one entry in the dashboard:

```yaml
pipeline:
  name: "relikquary-pr"
  project: "relikquary"
```

When absent, the project is inferred from the run's repository (the segment after the final `/`), so an
existing history needs no change. A name must match `[A-Za-z0-9._-]{1,64}`; one that does not is treated
as no project rather than being rewritten.

Projects with runs but no registered descriptor appear in the dashboard as **derived** — visible and
selectable, but not runnable until a descriptor is registered under the same name. Derived projects are
computed from the most recent `kontinuance.projects.derive-limit` runs (default 500); a project whose runs
have all aged past that window stops being listed.
```

- [ ] **Step 2: Record the feature in the roadmap**

Add 039 to the "Current state" table in `docs/roadmap.md` describing the auto-registry and the session gate, matching the style of the 032–037 entries.

- [ ] **Step 3: Commit the docs**

```bash
git add docs/running.md docs/roadmap.md
git commit -m "docs(039): document the project key and derived projects"
```

- [ ] **Step 4: Name the project in the hub's PR descriptor**

In `~/Projects/hestia-systems`, add `project: relikquary` under `pipeline:` in `platform/deploy/pipelines/relikquary-pr.yaml`, then refresh the runner's checkout so the daemon reads it:

```bash
ssh cirunner@hestia.local 'git -C ~/hestia-systems pull'
```

- [ ] **Step 5: Create the operator credential and wire it into the render**

Create the secret out-of-band (never committed — hub convention), then set **both** environment variables on the server Deployment through `render-kontinuance.sh`. Setting only one leaves the server open with a startup warning.

```bash
ssh cirunner@hestia.local 'export PATH=/opt/homebrew/bin:$PATH; \
  kubectl -n kontinuance-stage create secret generic kontinuance-auth \
    --from-literal=KONTINUANCE_AUTH_USERNAME=<user> \
    --from-literal=KONTINUANCE_AUTH_PASSWORD=<password>'
```

In the render script, add an `envFrom` referencing `kontinuance-auth` to the server container.

- [ ] **Step 6: Build, ship, render, publish**

```bash
cd ~/Projects/kontinuance
docker build --platform linux/arm64 -f deploy/server.Dockerfile -t kontinuance-server:local .
docker build --platform linux/arm64 -f deploy/web.Dockerfile    -t kontinuance-web:local .
docker save kontinuance-server:local | ssh cirunner@192.168.50.206 'export PATH=/opt/homebrew/bin:$PATH; colima ssh -- docker load'
docker save kontinuance-web:local    | ssh cirunner@192.168.50.206 'export PATH=/opt/homebrew/bin:$PATH; colima ssh -- docker load'

cd ~/Projects/hestia-systems
KONTINUANCE=~/Projects/kontinuance platform/deploy/scripts/render-kontinuance.sh stage
git add platform/deploy && git commit -m "cd(kontinuance): project auto-registry + operator auth" && git push origin HEAD:main
logos hestia gitea mirror-sync
```

- [ ] **Step 7: Verify against the running deployment**

```bash
# auth is now enforced: unauthenticated reads are rejected
curl -sk -o /dev/null -w '%{http_code}\n' https://kontinuance.stage.192.168.50.206.nip.io:30443/api/projects   # → 401
curl -sk https://kontinuance.stage.192.168.50.206.nip.io:30443/api/auth/me                                     # → authRequired: true
```

Then in a browser: sign in, confirm the picker lists `relikquary` with a `DERIVED` badge and its build count, select it, confirm the runs list is scoped to `relikquary-pr` runs, and confirm RUN PIPELINE is disabled with its reason shown.

- [ ] **Step 8: Update the hub's readiness notes**

In `ops/pipeline-readiness.md`, close the Phase 4 item *"Kontinuance endpoints unauthenticated (trigger/approve/reject)"* — the operator credential now gates them, demoting Cloudflare Access from the only lock to defense in depth. Note the two standing caveats: sessions are in-memory (a restart signs you out) and the single-replica RWO constraint is unchanged.

- [ ] **Step 9: Commit the hub changes**

```bash
cd ~/Projects/hestia-systems
git add platform/deploy/pipelines/relikquary-pr.yaml ops/runbooks/kontinuance-deploy-stage.md ops/pipeline-readiness.md
git commit -m "cd(kontinuance): name the relikquary project + enable operator auth"
```

---

## Self-Review

**Spec coverage:** FR-001 → Task 1. FR-002 → Task 2. FR-003 → Task 3. FR-004, FR-005 → Task 4. FR-006 → Tasks 4 (active pointer) and 5 (activation). FR-007 → Tasks 2 (field on the wire), 7 (projection), 8 (wiring). FR-008 → Task 9. FR-009 → Task 4's "registered wins" test. FR-010 → Task 10 Steps 5 and 7 (deployment; no code by design). FR-011 → Global Constraints, verified by the build steps in Tasks 1, 2, and 4. US5's "CI event source unaffected" is verified in Task 10 Step 7 by the gate continuing to post while auth is on.

**Deviations to flag:**

1. **Derivation window.** The spec does not mention one. `RunStore` exposes only `recent(limit)`, so a window is unavoidable; the plan makes it explicit and configurable (`kontinuance.projects.derive-limit`, default 500) and documents the consequence in Task 10 Step 1 rather than letting it be a silent cap.
2. **Web test strategy corrected during review.** The first draft of Tasks 6 and 9 used `@testing-library/svelte`, which this repo does not depend on — adding it would have violated the no-new-dependency constraint. Component behavior is asserted in Playwright against `web/e2e/mock.ts` fixtures instead, matching `app.spec.ts`; pure logic (Task 7) stays in a vitest `.test.ts`. The prop blocks in Tasks 8 and 9 were also rewritten against the real `Runs.svelte` and `Login.svelte` signatures, which take neither a `projects` nor a `runs`-with-props shape the draft assumed.

**Type consistency check:** `ProjectDto`'s five new fields (Task 4) match the `Project` interface fields (Task 6) by name and optionality. `ProjectResolver.resolve` (Task 3) and `runProject` (Task 7) implement the same precedence on the server and in the browser — if one changes, both must. `RunFilter` gains `project` in Task 7 and every construction site is updated in Task 8.
