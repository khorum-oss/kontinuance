# Kontinuance — Roadmap & Status

A living map of what Kontinuance is, what's built, the decisions that shape it, and the ordered
path forward. See [`overview.md`](overview.md) for the origin/vision and its v0–v3 phasing.

## What Kontinuance is

A Kotlin/Spring-Boot **CI/CD platform** — an in-house Jenkins/GitHub-Actions replacement. Three
planes (one process now, separable later): **orchestrator** (schedule/track runs), **agent**
(execute steps), **event source** (triggers). Pipelines are authored as a **hybrid YAML + Kotlin
DSL** generated on Konstellation KSP.

## Current state (2026-07-15)

| Feature | State |
|---|---|
| **001 pipeline-foundation** | **Built** — engine runs stages/steps in-process (coroutines/ProcessBuilder), hybrid YAML+Kotlin DSL, sealed-class status, secret masking, `StepDefinition`+`StepExecutor` seam. ⚠️ Lives on feature branches — **not yet merged to `main`** (main has only `dsl/common` scaffolding). |
| **002 typed-steps** | **Built** — `gradleStep`/`dockerStep`/`npmStep` on the 001 seam (models + DSL builders + executors + descriptor keys + tests); all impl tasks complete. Lives on feature branches alongside 001. |
| **003 github-event-source** | **US1 + US3 + runnable service built** (engine-only, Spring-free) — `github` module: poll GitHub (PRs **and** tracked-branch pushes) → run via the 001 engine → post commit status (`pending→success/failure`) on the head SHA, stable `kontinuance/ci` context, `KONTINUANCE_SHA` from the event. Manual trigger + native config + the installable `kontinuance-ci` service (poll loop, env token, durable cursor); quickstart in `examples/github-ci/`. No new external deps. **31 tests** incl. end-to-end. Remaining: US2 required-check *gating* (a branch-protection config step; the stable context is already guaranteed), the optional webhook mode, and the cursor→persistence fold-in. |
| **004 khorum-pattern-alignment** | **Built & merged** (PR #10 → `main`, v1.0.13) — fixed Kover/Sonar coverage aggregation to measure `:engine`, shared `config/detekt/detekt.yml`, `dependency.env` public/private switch, dedicated `integration-tests` module, per-feature `checklists/`, and Kotlin 2.1.20→2.3.21 (+KSP 2.3.10, KSP2). |
| **005 publish-artifacts** | **Built** — native publish pipeline example (`examples/publish-artifacts/`) + `sample-lib` + quickstart; publishes Maven artifacts to a configurable repo via the installed CLI, URL/creds as masked secrets. Verified end-to-end against a `file://` repo (full JAR/POM/checksums land; missing-secret fails fast with no upload). No engine change. |
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

3. **Persistence** *(006)* — run history + the durable poll cursor (folds in the 003 placeholder
   store). The store the UI lists runs/history from (beyond the current process's memory).
4. **Server / API + streaming layer** *(007 — UI prerequisite)* — a long-running Spring Boot
   orchestrator that hosts the engine and exposes it: HTTP for pipelines/runs, **SSE/WebSocket for
   live status + streamed logs** (the surface 001 deferred). This one layer unlocks the UI, gives
   003 a home for its status reporting, and carries remote approval actions. **Design the API contract
   *from* the finished UI** (the maintainer's screenshots) — build the endpoints the UI actually
   needs, not a speculative API.
5. **Approval / promotion step** *(008)* — makes the reserved `WaitingOnApproval` state actionable
   (environment promotion + manual approval), replacing today's manual prod pipeline. The UI's
   control surface drives it.
6. **Web UI** *(009+ — design already in hand; screenshots to spec against)* — SvelteKit frontend
   against the (4) API. Naturally splits by dependency:
   - **Observe UI** (read-only): list pipelines/runs, watch live status + streamed logs. Needs (4) +
     light (3) only — deliverable soon after the API layer.
   - **Control UI**: trigger runs, click-to-approve promotions. Additionally needs (5).
   The existing UI design guides the API shape (4) and where the observe/control line falls.
7. **Typed-step wrappers for the khorum DSLs** — `render`→**zosn**, `deploy`→**logos**,
   `UAT`→**euri** (Playwright), each a `StepExecutor` plugin.
8. **Runner isolation** (Docker/k8s) — overview v1 item; when parallel/multi-tenant runs matter.

## Cross-repo artifacts (today)

- **kontinuance**: `specs/003-github-event-source/*` (PR #4); the `engine→dsl` refactor commit.
- **hestia-systems**: `platform/deploy/pipelines/{relikquary-cd-stage,relikquary-promote-prod}.yaml`
  + README (PR #15) — the dogfood delivery descriptors.

## Immediate next step

**003 US1 is built.** Natural continuations, any order: **(a)** wire the long-running service +
config (repository bindings, poll interval, token) so it actually runs on the Mini — this is really
the front half of the **Server/API feature (007)**; **(b)** **US3** — push-to-`main` delivery +
manual trigger (small additions on the same plumbing); **(c)** **Persistence (006)** — swap the
file-cursor placeholder for the durable store and add run history. The UI cluster (007 API → 009 UI)
then builds on (a)+(c).
