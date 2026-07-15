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
| **003 github-event-source** | **Spec/plan/tasks drafted today** (PR #4). Poll-based external CI. **Blocked-on** the `engine→dsl` refactor merging. |
| **004 khorum-pattern-alignment** | **Built & merged** (PR #10 → `main`, v1.0.13) — fixed Kover/Sonar coverage aggregation to measure `:engine`, shared `config/detekt/detekt.yml`, `dependency.env` public/private switch, dedicated `integration-tests` module, per-feature `checklists/`, and Kotlin 2.1.20→2.3.21 (+KSP 2.3.10, KSP2). |
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
1. **Publish-artifacts enablement** *(005, next)* — the installable `kontinuance` CLI (done) plus a
   **native** publish pipeline example + quickstart, so artifacts can be published to a private repo
   from your own environment by hand. Descriptors are authored in Kontinuance's own schema — never
   copied from GitHub Actions or the `hestia-systems` descriptors.
2. **003 external CI (engine-only)** — poll → pending check → run → terminal → required-check gate;
   implemented against the current `engine` layout (the `engine→dsl` refactor is **deferred**, not a
   prerequisite). This is what lets Kontinuance *replace* GitHub Actions for PRs and merges, and
   auto-triggers the publish pipeline from (1).

**Then — make it a platform (maps to overview v1/v2)**
3. **Persistence** *(006)* — run history + the durable poll cursor (folds in the 003 placeholder store).
4. **Approval / promotion step** *(007)* — the prod gate as a first-class primitive (environment
   promotion + manual approval), replacing today's manual prod pipeline.
5. **Web UI** *(spec TBD — design from provided screenshots)* — a dashboard to view pipelines, runs,
   live/streamed logs, statuses, and to drive manual approvals/promotions. Expected to follow the
   khorum stack (SvelteKit frontend against an engine-backed API); persistence (3) is its natural
   backing store. Design to be pinned down from screenshots the maintainer will supply.
6. **Typed-step wrappers for the khorum DSLs** — `render`→**zosn**, `deploy`→**logos**,
   `UAT`→**euri** (Playwright), each a `StepExecutor` plugin.
7. **Runner isolation** (Docker/k8s) — overview v1 item; when parallel/multi-tenant runs matter.

## Cross-repo artifacts (today)

- **kontinuance**: `specs/003-github-event-source/*` (PR #4); the `engine→dsl` refactor commit.
- **hestia-systems**: `platform/deploy/pipelines/{relikquary-cd-stage,relikquary-promote-prod}.yaml`
  + README (PR #15) — the dogfood delivery descriptors.

## Immediate next step

**Publish-artifacts enablement (005):** ship a native publish pipeline example + quickstart on top of
the installable `kontinuance` CLI, so artifacts can be published to a private repo from the
maintainer's own environment — then implement **003 external CI (engine-only)** to auto-trigger it
from GitHub pushes/merges.
