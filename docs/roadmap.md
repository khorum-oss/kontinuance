# Kontinuance — Roadmap & Status

A living map of what Kontinuance is, what's built, the decisions that shape it, and the ordered
path forward. See [`overview.md`](overview.md) for the origin/vision and its v0–v3 phasing.

## What Kontinuance is

A Kotlin/Spring-Boot **CI/CD platform** — an in-house Jenkins/GitHub-Actions replacement. Three
planes (one process now, separable later): **orchestrator** (schedule/track runs), **agent**
(execute steps), **event source** (triggers). Pipelines are authored as a **hybrid YAML + Kotlin
DSL** generated on Konstellation KSP.

## Current state (2026-07-17)

| Feature | State |
|---|---|
| **001 pipeline-foundation** | **Built** — engine runs stages/steps in-process (coroutines/ProcessBuilder), hybrid YAML+Kotlin DSL, sealed-class status, secret masking, `StepDefinition`+`StepExecutor` seam. ⚠️ Lives on feature branches — **not yet merged to `main`** (main has only `dsl/common` scaffolding). |
| **002 typed-steps** | **Built** — `gradleStep`/`dockerStep`/`npmStep` on the 001 seam (models + DSL builders + executors + descriptor keys + tests); all impl tasks complete. Lives on feature branches alongside 001. |
| **003 github-event-source** | **US1 + US3 + runnable service built** (engine-only, Spring-free) — `github` module: poll GitHub (PRs **and** tracked-branch pushes) → run via the 001 engine → post commit status (`pending→success/failure`) on the head SHA, stable `kontinuance/ci` context, `KONTINUANCE_SHA` from the event. Manual trigger + native config + the installable `kontinuance-ci` service (poll loop, env token, durable cursor); quickstart in `examples/github-ci/`. No new external deps. **31 tests** incl. end-to-end. Remaining: US2 required-check *gating* (a branch-protection config step; the stable context is already guaranteed), the optional webhook mode, and the cursor→persistence fold-in. |
| **004 khorum-pattern-alignment** | **Built & merged** (PR #10 → `main`, v1.0.13) — fixed Kover/Sonar coverage aggregation to measure `:engine`, shared `config/detekt/detekt.yml`, `dependency.env` public/private switch, dedicated `integration-tests` module, per-feature `checklists/`, and Kotlin 2.1.20→2.3.21 (+KSP 2.3.10, KSP2). |
| **005 publish-artifacts** | **Built** — native publish pipeline example (`examples/publish-artifacts/`) + `sample-lib` + quickstart; publishes Maven artifacts to a configurable repo via the installed CLI, URL/creds as masked secrets. Verified end-to-end against a `file://` repo (full JAR/POM/checksums land; missing-secret fails fast with no upload). No engine change. |
| **006 run-persistence** | **Built** — new `persistence` module: durable `RunStore` (file-backed JSON, `recent(limit)` newest-first + `get(id)`, corrupt-record isolation) behind a swappable seam; `RunRecord` captures status + CI context (repo/sha/trigger), no secrets/logs. The `github` CI service records every run; `kontinuance-ci` state consolidated under `~/.kontinuance/`. Engine-only, Spring-free, no new deps. |
| **007 server-api** | **Read API built** (increment 1, Spring-free) — new `server` module: `GET /api/health`, `/api/runs?limit=N` (newest-first, default 50 / cap 500), `/api/runs/{id}` (404 absent). Transport-agnostic `RunApi` handlers + `HttpApiServer` on the JDK HttpServer + installable `kontinuance-api` launcher (host/port/store configurable) serving the 006 store. No new deps; verified via real `HttpClient` round-trip + live `curl`. **Superseded by 008** (real Spring Boot); the JDK `HttpApiServer` transport was retired there. Still deferred: SSE/WebSocket streaming, manual-trigger POST, auth. |
| **008 spring-boot-server** | **Built** — migrated `:server` off the JDK HttpServer to **Spring Boot 4.1.0** (WebFlux + actuator) with Kotlin coroutines: suspend `@RestController` over a suspend `RunReadFacade` (`withContext(Dispatchers.IO)` offloading the blocking file store), reusing the 007 `RunApi`/`ApiResponse`/`JsonView` unchanged. Same `/api` contract byte-for-byte + `/actuator/health`. Versions via the Boot BOM (starters pinned, no Boot Gradle plugin) so **dependency verification stays enabled** — extended with group trusts for the Spring/test ecosystem, never disabled. `@SpringBootTest(RANDOM_PORT)` + `WebTestClient` real HTTP round-trip (15 tests green on Gradle 8.14.3; CI 9.5.1 authoritative for the new graph). |
| **009 web-ui** | **Built** — a SvelteKit + Storybook "mission control" dashboard (`web/`) over the 008 read API + live stream. Seven screens behind a sidebar/topbar shell: **Runs** (newest-first, wired to `/api/runs`), **live** updates over SSE (`/api/runs/stream`), **Run detail** (log view + Kover coverage sidebar), plus **Pipeline / Deploy / Coverage / Config** wired to four new **additive stub endpoints** on `:server` (`/api/runs/{id}/pipeline`, `/api/deploy`, `/api/coverage`, `/api/config`) — no new JVM dep, verification untouched. Component-library-first (Storybook stories per element/screen on typed fixtures). Tests: Vitest unit + Playwright E2E, wired into a `web-tests` CI job. Dark teal theme; Space Grotesk + JetBrains Mono. Since built: **auth wired** (016 server + 017 web), **write actions** (trigger/approve via 010/011), and **real step logs** (018 — run detail shows recorded masked output). Still deferred: real pipeline/deploy/coverage sources (stubs today) and a live **SSE/WebSocket log-tail** (018 records + polls; push tail is the follow-up). |
| `engine→dsl` refactor | **Deferred, not a blocker.** 003 will be implemented **engine-only** (depending on `engine` types directly), so this refactor is decoupled from the near-term path and can land later on its own merit. |

## Decisions locked today (2026-07-12)

- **CD for Hestia/Relikquary moves to Kontinuance** (in-house); GitHub Actions dropped for delivery.
- **External CI is poll-first**: Kontinuance polls GitHub (outbound only — **no inbound exposure,
  no Cloudflare required**), posts a **commit status** (`pending→success/failure`) on the PR head
  SHA, and a **required status check** gates the merge. A signature-verified **webhook via a
  Cloudflare Tunnel** is an *optional later* latency mode, never a prerequisite.
- **Commit Status API for v1** (works with a PAT); Checks API deferred (needs a GitHub App).
- **Thin, mockable `GitHubClient`** integration-tested against a mock API (Constitution II).
- **Delivery is a Kontinuance pipeline**: drafted `relikquary-cd-stage` + `relikquary-promote-prod`
  descriptors (in `hestia-systems/platform/deploy/pipelines/`) as the **dogfood target** for the
  001 engine. Prod is a *separate manual pipeline* = the promotion gate; it **promotes by digest**.

## Roadmap (ordered)

> Numbering note: `specs/` now holds 001 (foundation), 002 (typed steps), 003 (github event source,
> drafted), 004 (khorum pattern alignment, **merged**). New features continue at **005+**. The
> conceptual items below list their expected spec number where one is assigned.

**Near-term — make it usable**
1. **Publish-artifacts enablement** *(005 — ✅ built)* — the installable `kontinuance` CLI plus a
   **native** publish pipeline example (`examples/publish-artifacts/`) + quickstart, so artifacts are
   published to a private repo from your own environment by hand. Descriptors are authored in
   Kontinuance's own schema — never copied from GitHub Actions or the `hestia-systems` descriptors.
   Verified end-to-end against a `file://` repo.
2. **003 external CI (engine-only)** — *US1 MVP ✅ built*: poll → pending status → run via the engine
   → terminal `success`/`failure` on the head SHA (stable `kontinuance/ci` context, `KONTINUANCE_SHA`
   from the event). Implemented against the current `engine` layout (the `engine→dsl` refactor stays
   **deferred**), Spring-free, no new deps, 19 tests incl. end-to-end. This lets Kontinuance *replace*
   GitHub Actions for PRs. **Remaining:** US2 required-check gating (branch-protection config + the
   already-honored stable context) and US3 push-to-`main` delivery / manual trigger / optional
   webhook; the long-running service wiring arrives with the Server/API feature (007).

**Then — make it a platform (maps to overview v1/v2 — "persistent state … basic UI")**

The Web UI sits at the top of a short dependency chain. Today Kontinuance is a **CLI + in-process
engine with no long-running server**, so the true gating prerequisite for any UI is a server/API +
streaming layer — not persistence or approval. What 001 already provides for free: the sealed
`PipelineStatus` FSM (incl. the reserved `WaitingOnApproval` state) and `StatusEvent` transitions over
a `Flow`/`SharedFlow` — the UI's live data model already exists; it just isn't exposed over a wire
(001 explicitly deferred "Web UI and remote log streaming over SSE/WebSocket" to v1+).

3. **Persistence** *(006 — ✅ built)* — durable `RunStore` (file-backed, newest-first listing +
   fetch-by-id, corrupt-record isolation) recording every CI run with status + context; state under
   `~/.kontinuance/`. The store the UI lists runs/history from. A DB backend can replace the file
   default behind the `RunStore` seam when the Server/API arrives.
4. **Server / API + streaming layer** *(007 read API ✅ + 008 Spring Boot ✅ + live streaming ✅)* — a
   long-running **Spring Boot 4.1 (WebFlux + actuator)** orchestrator exposing the run history: HTTP for
   pipelines/runs (built) **and live SSE (`GET /api/runs/stream`) + WebSocket (`/ws/runs`)** streaming run
   records as they appear (the surface 001 deferred), built on the coroutine/`Flow` base 008 established —
   a cold `RunStream` Flow (initial snapshot + polled updates, blocking read offloaded) fanned out to both
   transports. This layer unlocks the UI, gives 003 a home for its status reporting, and carries remote
   approval actions. **Design the remaining API contract *from* the finished UI** (the maintainer's
   screenshots). *Next here:* streamed step **logs** (vs. run-status records) and a push/DB-notify source
   behind the same `RunStream` seam to replace polling. **Design the API contract *from* the finished UI.**
5. **Web UI** *(009 — ✅ built)* — SvelteKit + Storybook "mission control" dashboard (`web/`) against the
   (4) API. The **Observe UI** is done (read-only): sign-in shell, runs list, live status over SSE, run
   detail, and pipeline/deploy/coverage/config screens (the last four on additive server stubs). The
   **Control UI** (trigger runs, click-to-approve promotions) is the remaining slice — it needs write
   endpoints + approval (below).
6. **Approval / promotion step** *(010)* — makes the reserved `WaitingOnApproval` state actionable
   (environment promotion + manual approval), replacing today's manual prod pipeline. The UI's
   control surface drives it.
7. **Typed-step wrappers for the khorum DSLs** — `render`→**zosn**, `deploy`→**logos**,
   `UAT`→**euri** (Playwright), each a `StepExecutor` plugin.
8. **Runner isolation** (Docker/k8s) — overview v1 item; when parallel/multi-tenant runs matter.

## Cross-repo artifacts (today)

- **kontinuance**: `specs/003-github-event-source/*` (PR #4); the `engine→dsl` refactor commit.
- **hestia-systems**: `platform/deploy/pipelines/{relikquary-cd-stage,relikquary-promote-prod}.yaml`
  + README (PR #15) — the dogfood delivery descriptors.

## Immediate next step

**Publishing (005), external CI (003), persistence (006), the read API (007), the Spring Boot migration
(008), and the Web UI (009)** are all built — the engine runs, delivers, gates GitHub PRs, records
history, serves it from a real Spring Boot 4.1 (WebFlux + coroutine) app with live SSE updates, and now
has a **SvelteKit observe dashboard** over all of it. The **Control UI** is what remains: make the
forward-looking screens real. Done since: **write endpoints** (manual trigger + approve/promote behind the
`WaitingOnApproval` FSM, features 010/011) and **real step logs** (018 — the run detail shows the run's
recorded masked output, refreshed while active). Remaining: **real data sources** for the pipeline/deploy
stubs, a live **SSE/WebSocket log-tail** to push lines as they occur (018 records + polls today), and a
push/DB-notify source to retire polling behind the `RunStream` seam.
