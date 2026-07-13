# Implementation Plan: GitHub Event Source & External-CI Integration

**Branch**: `003-github-event-source` | **Date**: 2026-07-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-github-event-source/spec.md`

## Summary

Add the **event source** plane so a GitHub PR or push starts a pipeline run (via the
existing 001 `PipelineEngine`) and its outcome posts back to GitHub as a **commit status**
on the head SHA (`pending → success | failure`), letting a **required status check** gate
merges. Trigger by **polling** GitHub (outbound only — the LAN is never exposed); an
optional signature-verified webhook (behind a Cloudflare Tunnel) is a later latency mode
with identical downstream. New self-contained `github` module; the engine and DSL are
unchanged — this feature only *triggers* and *reports*.

## Technical Context

**Language/Version**: Kotlin 2.1.20 (repo current), JDK 21; Spring Boot (platform baseline
per constitution).

**Primary Dependencies**: Spring Boot (DI, config, scheduling), Kotlin coroutines (poll
loop + non-blocking run coordination, as in the engine), an HTTP client for the GitHub
REST API (Phase 0 decision: `org.kohsuke:github-api` vs a thin client over Spring
`WebClient`/OkHttp — lean thin-client for a mockable seam), the existing `engine`
(`PipelineEngine`, `PipelineDescriptor`) and `dsl` (`Run`, `PipelineStatus`) modules.

**Storage**: minimal durable **poll cursor** per repo only. Placeholder store (a small
file or embedded H2/SQLite) explicitly flagged to fold into the future persistence
feature; **no run-history persistence in this feature**.

**Testing**: JUnit 5 + MockK for unit (event→pipeline resolution, dedup, `Run`→status
mapping); **`@SpringBootTest` + WireMock (or Testcontainers)** for the GitHub client
integration — the sole external seam, exercised end-to-end (poll → status create → status
update) with **zero real-network dependency** (Constitution II).

**Target Platform**: JVM service on the Hestia Mini (self-hosted, single node).

**Project Type**: Multi-module Gradle platform (backend service). Adds one module: `github`.

**Performance Goals**: configurable poll interval (default 30–60s); a handful of repos, low
PR volume; correctness and rate-limit friendliness over throughput.

**Constraints**: **outbound-only** (no inbound exposure in the default mode); honor GitHub
rate-limit / `Retry-After` with backoff (no hot-loop); tokens + step secrets masked
everywhere (logs, status text); idempotent triggering.

**Scale/Scope**: homelab — the Hestia/Relikquary repos plus future khorum apps; single
Kontinuance instance.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Platform-First & Stable Public Contract** — the **check-context string** and the
  repository-binding/trigger **config schema** are consumer-facing contracts → they MUST be
  stable and semver-governed (branch-protection rules match the context by name; changing it
  silently breaks gating). ✅ Enforced by FR-004; a test pins the context string.
- **II. Test-First & Integration-Verified** — GitHub is an external integration, so it MUST
  be behind an interface and covered by an integration test against a **mock GitHub API**;
  behavior added test-first (Red→Green). ✅ FR-011 + the WireMock harness below.
- **III. Quality Gates Non-Negotiable** — detekt zero-violations, Kover verification,
  SonarCloud not regressed, on the new module. ✅ Planned; no gate weakened.
- **IV. Correct, Covered Code Generation** — **N/A**: this feature adds no KSP-generated
  code (it consumes the existing DSL model; it does not extend the generator).
- **V. Supply-Chain Integrity & Reproducible Publishing** — GitHub App key / token supplied
  via **environment / untracked config only**, never committed or logged; any new
  dependency (HTTP/GitHub client, WireMock) added to `gradle/verification-metadata.xml`. ✅
  FR-010/FR-014.

**Result**: PASS — no violations; Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```text
specs/003-github-event-source/
├── plan.md              # This file
├── research.md          # Phase 0 output (GitHub API surface, client choice, poll strategy)
├── data-model.md        # Phase 1 output (entities: RepositoryBinding, TriggerEvent, RunReport, PollCursor, GitHubConnection)
├── quickstart.md        # Phase 1 output (configure a repo binding; run locally against WireMock)
├── contracts/           # Phase 1 output (GitHubClient, RunReporter, EventSource, CursorStore interfaces)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
github/                                        # NEW module (registered in settings.gradle.kts)
└── src/
    ├── main/kotlin/org/khorum/oss/kontinuance/github/
    │   ├── client/        GitHubClient (interface) + RestGitHubClient, GitHubConnection, RateLimit
    │   ├── poll/          Poller (coroutine/@Scheduled loop), PollCursor, CursorStore (+ file/H2 impl)
    │   ├── trigger/       TriggerEvent, RepositoryBinding, TriggerResolver (event → pipeline)
    │   ├── report/        RunReporter (Run → commit status), CheckContext
    │   ├── webhook/        WebhookReceiver + signature verify   # OPTIONAL (US3/FR-013), off by default
    │   └── config/        GitHubProperties, RepositoryBindingConfig
    └── test/kotlin/org/khorum/oss/kontinuance/github/
        ├── unit/          resolver / dedup / status-mapping (MockK, no network)
        └── integration/   GitHubClientIT + EventSourceIT against WireMock (poll→pending→terminal)

engine/  dsl/                                   # UNCHANGED — consumed, not modified
  (github depends on engine: PipelineEngine, PipelineDescriptor; and dsl: Run, PipelineStatus)
```

**Structure Decision**: A single new **`github` module** holding the three separable
concerns as packages — `poll` (event source), `trigger` (event→pipeline resolution), and
`report` (outbound status) — around a mockable `client`. This honors the overview's
orchestrator/agent/**event-source** split as *classes now, processes later*, and keeps the
one external dependency (GitHub) behind one interface for Constitution-II integration
testing. The engine/dsl are consumed unchanged: the source calls
`PipelineEngine.run(descriptor)` and maps the returned `Run`/`PipelineStatus` to a status;
`SecretMasker` is reused for the reporter.

## Phases (Speckit)

- **Phase 0 — research.md**: (a) GitHub client choice — thin client over `WebClient`/OkHttp
  vs `github-api`; lean thin for the mock seam. (b) **Commit Status API vs Checks API** —
  recommend **Commit Status** for v1 (works with a PAT, matches required-status-check gating,
  minimal surface); Checks API (richer, needs a GitHub App) as a later enhancement. (c) Poll
  strategy — list open PRs + compare head SHA to cursor; `since`/ETag to stay rate-limit
  friendly. (d) Cursor store placeholder (file vs embedded H2) pending the persistence
  feature. (e) rate-limit/backoff handling.
- **Phase 1 — design**: data-model.md (the five entities), contracts/ (`GitHubClient`,
  `EventSource`, `RunReporter`, `CursorStore` interfaces + the status state machine),
  quickstart.md (bind a repo → open a PR against WireMock → see pending→success). Re-run the
  Constitution Check.
- **Phase 2 — tasks.md**: produced by `/speckit-tasks` (not here); expected shape: contract
  tests first (WireMock), then poller, resolver, reporter, cursor, then the optional webhook.

## Complexity Tracking

No constitution violations — table intentionally empty.

## Coordination note

The repo has an in-flight `engine → dsl` model refactor. This plan targets the **post-
refactor** layout (`dsl/model/Run`, `engine/execution/PipelineEngine`,
`engine/descriptor/PipelineDescriptor`). Land that refactor before `/speckit-tasks` so the
contracts in Phase 1 pin the settled interfaces.
