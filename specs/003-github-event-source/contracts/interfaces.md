# Phase 1 Contracts: GitHub Event Source

Interface seams for `003`. Signatures are indicative (Kotlin); the **contract tests**
(`/speckit-tasks` writes these first) assert the behaviors noted, not the exact syntax.
All live in the new `github` module; `Run`/`PipelineStatus` come from `dsl`, the engine
from `engine`.

## GitHubClient — the one external seam (mocked in integration tests)

```kotlin
interface GitHubClient {
    /** Open PRs; pass the prior ETag for a conditional request (304 ⇒ NotModified, no rate cost). */
    fun listOpenPullRequests(owner: String, repo: String, etag: String?): PullRequestPage
    /** Head commit of a branch (for push/delivery triggers). */
    fun branchHead(owner: String, repo: String, branch: String): CommitRef
    /** Create/update a commit status on [sha]. Idempotent per (context, sha). */
    fun createStatus(owner: String, repo: String, sha: String, status: CommitStatus)
}
```
**Contract**: on `403`/secondary-rate-limit the impl surfaces a typed `RateLimited(reset)`;
on `401` a typed `AuthFailed`; credentials never appear in exceptions or logs. Integration
tests drive all three via WireMock.

## EventSource — produces normalized triggers

```kotlin
interface EventSource {
    /** One sweep: return new TriggerEvents (PR heads / pushes not yet in the cursor). */
    suspend fun poll(binding: RepositoryBinding, cursor: PollCursor): PollResult
}   // PollResult(events: List<TriggerEvent>, nextCursor: PollCursor)
```
**Contract**: re-polling with an up-to-date cursor yields **no** events for an already-handled
SHA (idempotency, SC-003); a new push (new head SHA) yields one event.

## RunReporter — maps a Run to a GitHub status

```kotlin
interface RunReporter {
    suspend fun reportPending(event: TriggerEvent)
    suspend fun reportOutcome(event: TriggerEvent, run: Run)   // Run from dsl/model
}
```
**Contract**: `context` == `binding.checkContext` (byte-stable across runs, FR-004/SC-002);
`PipelineStatus` → state mapping per data-model; description is `SecretMasker`-cleaned;
outbound failures retry with backoff and never drop the terminal report (FR-007).

## CursorStore — durable poll cursor (persistence placeholder)

```kotlin
interface CursorStore {
    fun load(owner: String, repo: String): PollCursor?
    fun save(cursor: PollCursor)
}
```
**Contract**: survives process restart (SC-005); bounded `lastHandledShas`. File/H2 impl now;
swappable when the persistence feature lands.

## TriggerService — orchestrates the loop (wires the above + the engine)

```kotlin
// pseudocode of one repo sweep
val result = eventSource.poll(binding, cursorStore.load(owner, repo) ?: PollCursor.empty())
for (event in result.events) {
    if (event.kind == PR) reporter.reportPending(event)
    val run = pipelineEngine.run(resolvePipeline(binding, event))   // engine/execution
    if (event.kind == PR) reporter.reportOutcome(event, run)
}
cursorStore.save(result.nextCursor)
```
**Contract**: PENDING is posted **before** the run starts; the terminal status is posted
**after** the `Run` completes; the SHA is recorded in the cursor only after a successful
terminal report (at-least-once report, exactly-once run per SHA).
