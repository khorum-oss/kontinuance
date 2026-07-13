# Kontinuance — Roadmap & Status

A living map of what Kontinuance is, what's built, the decisions that shape it, and the ordered
path forward. See [`overview.md`](overview.md) for the origin/vision and its v0–v3 phasing.

## What Kontinuance is

A Kotlin/Spring-Boot **CI/CD platform** — an in-house Jenkins/GitHub-Actions replacement. Three
planes (one process now, separable later): **orchestrator** (schedule/track runs), **agent**
(execute steps), **event source** (triggers). Pipelines are authored as a **hybrid YAML + Kotlin
DSL** generated on Konstellation KSP.

## Current state (2026-07-12)

| Feature | State |
|---|---|
| **001 pipeline-foundation** | **Built** — engine runs stages/steps in-process (coroutines/ProcessBuilder), hybrid YAML+Kotlin DSL, sealed-class status, secret masking, `StepDefinition`+`StepExecutor` seam. ⚠️ Lives on feature branches — **not yet merged to `main`** (main has only `dsl/common` scaffolding). |
| **002 typed-steps** | Spec/plan/tasks drafted; **26 open impl tasks** — `gradleStep`/`dockerStep`/`npmStep` on the 001 seam. |
| **003 github-event-source** | **Spec/plan/tasks drafted today** (PR #4). Poll-based external CI. **Blocked-on** the `engine→dsl` refactor merging. |
| `engine→dsl` refactor | Committed today (wip) on `claude/specify-implement-7gqci3` — moves the pipeline model + secret sources into a `dsl` module. Local, not pushed. |

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

**Near-term — unblock & prove**
1. Land the **`engine→dsl` refactor** and the **001 engine** on `main` (main is behind the built v0).
2. **Dogfood**: run `relikquary-cd-stage.yaml` manually on the Mini → proves 001 against a real
   pipeline (build→push→render→sync→UAT) and feeds fixes back into 002/003.

**Mid-term — the pipeline the Hestia flow needs**
3. Implement **002 typed steps** (`dockerStep`/`gradleStep`) — the publish/build steps become typed.
4. Implement **003 external CI** (poll → pending check → run → terminal → required-check gate) —
   the piece that lets Kontinuance *replace* GitHub Actions for PRs and merges.

**Then — make it a platform (maps to overview v1/v2)**
5. **004 Persistence** — run history + the durable poll cursor (folds in the 003 placeholder store).
6. **005 Approval / promotion step** — the prod gate as a first-class primitive (environment
   promotion + manual approval), replacing today's manual prod pipeline.
7. **Typed-step wrappers for the khorum DSLs** — `render`→**zosn**, `deploy`→**logos**,
   `UAT`→**euri** (Playwright), each a `StepExecutor` plugin.
8. **Runner isolation** (Docker/k8s) — overview v1 item; when parallel/multi-tenant runs matter.

## Cross-repo artifacts (today)

- **kontinuance**: `specs/003-github-event-source/*` (PR #4); the `engine→dsl` refactor commit.
- **hestia-systems**: `platform/deploy/pipelines/{relikquary-cd-stage,relikquary-promote-prod}.yaml`
  + README (PR #15) — the dogfood delivery descriptors.

## Immediate next step

Merge the refactor + 001 to `main`, then dogfood `relikquary-cd-stage.yaml` — that single run
validates the whole 001 engine against production-shaped work before 002/003 land.
