# Phase 1 Data Model: GitHub Event Source & External-CI Integration

Entities the feature introduces. Persistence is minimal (only `PollCursor` is durable);
everything else is in-memory/config. Reuses `dsl/model` `Run`, `PipelineStatus`, `SecretRef`.

## RepositoryBinding

Config linking a GitHub repo to the pipeline(s) it runs. Loaded from `GitHubProperties`.

| Field | Type | Notes |
|---|---|---|
| `owner` | String | GitHub org/user |
| `repo` | String | repository name |
| `prPipeline` | String? | pipeline (descriptor id/path) run for PR events; null ⇒ no PR check |
| `pushPipeline` | String? | pipeline run on push to a tracked branch; null ⇒ no delivery |
| `trackedBranches` | List\<String> | branches whose pushes trigger `pushPipeline` (e.g. `["main"]`) |
| `checkContext` | String | **stable** status context (default `kontinuance/<prPipeline>`); FR-004 |

**Rules**: `checkContext` MUST NOT change for a binding once published (branch-protection
matches it). At least one of `prPipeline`/`pushPipeline` MUST be set.

## TriggerEvent

Normalized event produced by the poller (or webhook), consumed by the resolver/runner.

| Field | Type | Notes |
|---|---|---|
| `binding` | RepositoryBinding | which repo |
| `kind` | enum `PR` \| `PUSH` \| `MANUAL` | source of the trigger |
| `headSha` | String | commit to run + report on (idempotency token) |
| `ref` | String | branch/ref (e.g. `refs/heads/feature-x`) |
| `prNumber` | Int? | present for `PR` |

**Rules**: identity for dedup = `(owner, repo, headSha, pipeline)`. A `MANUAL`/`PUSH` event
with no PR reports no commit-status by default (records outcome only).

## RunReport

Outbound payload mapping a 001 `Run` outcome to a GitHub commit status.

| Field | Type | Notes |
|---|---|---|
| `sha` | String | `TriggerEvent.headSha` |
| `context` | String | `RepositoryBinding.checkContext` (stable) |
| `state` | enum `PENDING` \| `SUCCESS` \| `FAILURE` \| `ERROR` | from `PipelineStatus` |
| `description` | String | short, **secret-masked**; on failure names the failing step |
| `targetUrl` | String? | link to the run/logs when available |

**Mapping** (`PipelineStatus` → `state`): running/queued → `PENDING`; all-success →
`SUCCESS`; any step failed → `FAILURE`; engine/infra error → `ERROR`. Description passes
through `SecretMasker`.

## PollCursor  *(the only durable entity)*

Per-repo marker so polling resumes without miss/re-process.

| Field | Type | Notes |
|---|---|---|
| `owner` / `repo` | String | key |
| `lastHandledShas` | Set\<String> \| bounded | head SHAs already dispatched (dedup) |
| `etag` | String? | last conditional-request ETag (rate-limit friendliness) |
| `updatedAt` | Instant | bookkeeping |

Stored via `CursorStore` (file/H2 placeholder → future persistence feature). Bounded so it
doesn't grow unbounded (evict old SHAs).

## GitHubConnection

Authenticated client + rate-limit state; the sole seam mocked in integration tests.

| Field | Type | Notes |
|---|---|---|
| `client` | GitHubClient | thin interface (D3) |
| `credential` | SecretRef | PAT (App key later); env-sourced, never logged |
| `rateLimit` | RateLimit | remaining/reset; drives backoff |

## State transitions (per trigger)

```
observe(headSha) ─▶ [dedup? already handled] ─▶ drop
        │ new
        ▼
create status PENDING ─▶ engine.run(pipeline) ─▶ map Run → SUCCESS|FAILURE|ERROR
        │                                            │
        └────────────── retry/backoff on GitHub 5xx/rate-limit ─┘
                        update status (terminal) ─▶ record headSha in cursor
```
