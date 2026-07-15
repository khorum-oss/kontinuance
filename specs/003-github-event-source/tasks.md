# Tasks: GitHub Event Source & External-CI Integration

> ## ✅ Implementation status — 2026-07-15 (engine-only, Spring-free)
>
> **US1 (P1) MVP delivered** in the `github` module — poll → run via the 001 engine → report a commit
> status (`pending → success | failure`) on the head SHA. Built **engine-only** (depends on `:engine`
> alone; `Run`/`PipelineStatus` live there — the `engine→dsl` refactor is deferred, **not** a blocker)
> and **Spring-free** (coroutine poll loop; no Spring/`@Scheduled`). **No new dependency**: the client
> uses JDK `java.net.http.HttpClient`, JSON uses the catalog's `kotlinx-serialization-json`, and the
> integration seam is a JDK `HttpServer` fake (no WireMock → no `verification-metadata` churn). See the
> plan's "Engine-only + Spring-free adaptation" note.
>
> **Delivered files** (`github/src/main/.../github/`): `client/{GitHubModels,GitHubClient,RestGitHubClient}`,
> `report/{CheckContext,RunReporter}`, `trigger/{TriggerEvent,RepositoryBinding,TriggerResolver}`,
> `poll/{CursorStore(+InMemory/File),Poller}`, `EventSource`.
> **Tests (19, green):** `RestGitHubClientIT` (real HTTP round-trip), `EventSourceIT` (end-to-end
> poll→run→pending→terminal, real engine), `RunReporterTest` (status mapping + retry/backoff),
> `TriggerResolverTest`, `PollerTest` (dedup). Covers FR-001/002/003/004 and the stable check context;
> `KONTINUANCE_SHA` is injected into each run from the trigger event.
>
> **Deferred (own follow-up passes):** US2 required-check gating (mostly branch-protection config +
> the stable-context guarantee, already honored), US3 push-to-`main` delivery / manual trigger /
> optional webhook, the durable-cursor→persistence fold-in, and the long-running service wiring
> (arrives with the Server/API feature). The task list below is the original Spring/post-refactor
> draft, kept for reference; the engine-only delivery above supersedes its US1 items.

**Feature**: `003-github-event-source` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

**Organization**: grouped by user story (US1 P1 → US3 P3) so each is independently
implementable/testable. Test-first (Constitution II): contract/integration tests precede
implementation. `[P]` = parallelizable (different files, no dependency).

**Module root**: `github/` — `github/src/main/kotlin/org/khorum/oss/kontinuance/github/` (impl),
`github/src/test/kotlin/...` (tests). Consumes `:engine` (`PipelineEngine`, `PipelineDescriptor`)
and `:dsl` (`Run`, `PipelineStatus`, `SecretRef`).

> **Blocked-on**: the `engine→dsl` refactor (branch `claude/specify-implement-7gqci3`) must
> merge to `main` first — these tasks reference the settled `dsl/model` + `engine/execution`
> packages.

---

## Phase 1: Setup

- [ ] T001 Create the `github` Gradle module (`github/build.gradle.kts`) depending on `:engine`
  + `:dsl`; register it in `settings.gradle.kts`.
- [ ] T002 [P] Add dependencies: HTTP client (Spring `WebClient` or OkHttp), and test deps
  WireMock + MockK; record every new/updated dependency in `gradle/verification-metadata.xml`
  (Constitution V).
- [ ] T003 [P] Wire detekt + Kover for the module (inherit repo config; zero violations gate).

## Phase 2: Foundational (blocking prerequisites)

- [ ] T004 [P] Entities in `github/.../trigger/` + `report/`: `RepositoryBinding`,
  `TriggerEvent` (kind PR|PUSH|MANUAL), `RunReport`, `PollCursor` — per data-model.md.
- [ ] T005 [P] Seam interfaces in the matching packages: `GitHubClient` (`client/`),
  `EventSource` (`poll/`), `RunReporter` (`report/`), `CursorStore` (`poll/`) — per
  contracts/interfaces.md.
- [ ] T006 `GitHubProperties` + `RepositoryBindingConfig` (`config/`, Spring
  `@ConfigurationProperties`); token bound from env only (Constitution V).
- [ ] T007 `GitHubConnection` holding client + `RateLimit`; reuse `engine/logging/SecretMasker`
  in a small `ReporterMasking` helper.

---

## Phase 3: User Story 1 — PR triggers a run, outcome posts as a check (P1) 🎯 MVP

### Tests (write first, must fail)

- [ ] T008 [P] [US1] Contract test `GitHubClientContractIT` (WireMock): `createStatus` posts the
  correct payload for PENDING/SUCCESS/FAILURE with a fixed `context`; `403` → typed
  `RateLimited`; `401` → typed `AuthFailed`; secrets never in the request log.
- [ ] T009 [P] [US1] Integration test `EventSourceIT` (WireMock, no network): one open PR (head
  `abc`) → a PENDING status is posted → the engine runs a trivial descriptor → a terminal
  SUCCESS (all steps 0) / FAILURE (a step non-zero, description names the step) is posted.
- [ ] T010 [P] [US1] Unit tests: `PipelineStatus`→`RunReport.state` mapping; `TriggerResolver`
  picks `prPipeline` for a PR event.

### Implementation

- [ ] T011 [US1] `RestGitHubClient` implementing `GitHubClient` (list open PRs, create status)
  with typed `RateLimited(reset)` / `AuthFailed`; conditional-request (ETag) support.
- [ ] T012 [US1] `Poller` implementing `EventSource`: list open PRs, diff head SHA vs
  `PollCursor`, emit `TriggerEvent(PR)`; return `nextCursor`.
- [ ] T013 [US1] `RunReporter`: `reportPending` + `reportOutcome(event, run)`; description via
  masking; outbound retry with backoff (never drops the terminal report).
- [ ] T014 [US1] `TriggerService`: per-repo sweep — poll → (PR ⇒ reportPending) →
  `PipelineEngine.run(resolve(binding,event))` → (PR ⇒ reportOutcome) → `cursor.save`;
  idempotent on `(owner,repo,headSha,pipeline)`.
- [ ] T015 [US1] Spring scheduled poll loop at `poll-interval`; file-backed `CursorStore` impl.

**Checkpoint**: opening a PR on a configured repo shows a Kontinuance pending→success/failure
check on the head SHA — with no inbound connection. (SC-001, SC-004)

---

## Phase 4: User Story 2 — required check gates merge (P2)

### Tests

- [ ] T016 [P] [US2] Test: `RepositoryBinding.checkContext` is byte-stable across two runs of
  the same binding (pins the branch-protection contract, FR-004/SC-002).

### Implementation

- [ ] T017 [US2] Derive + freeze `checkContext` (default `kontinuance/<prPipeline>`); document
  the one-time branch-protection "required status check" setup (quickstart.md already covers).

**Checkpoint**: with the required-check rule set, a PR is unmergeable until Kontinuance reports
success; the context matches across runs without reconfiguration. (SC-002)

---

## Phase 5: User Story 3 — push + manual triggers (P3)

### Tests

- [ ] T018 [P] [US3] Integration test: a push to a tracked branch (`main`) runs the
  `pushPipeline` for the pushed SHA.
- [ ] T019 [P] [US3] Unit: manual trigger resolves an arbitrary ref → SHA → run; a non-PR run
  records outcome without posting a PR status.
- [ ] T020 [P] [US3] Idempotency: the same event observed twice (poll overlap / re-delivery)
  starts exactly one run. (SC-003)

### Implementation

- [ ] T021 [US3] `Poller`: track `branchHead` for `trackedBranches`, emit `TriggerEvent(PUSH)`.
- [ ] T022 [US3] Manual trigger entrypoint (small API endpoint or CLI) → `TriggerEvent(MANUAL)`.
- [ ] T023 [US3] Dedup layer in `TriggerService` keyed on `(owner,repo,headSha,pipeline)`.

**Checkpoint**: merges to `main` trigger the delivery pipeline; operators can re-run manually;
no double-runs. (US3)

---

## Phase 6: Optional webhook mode (FR-013) — off by default

- [ ] T024 [P] `WebhookReceiver` (Spring controller) with **signature verification**, feeding
  the same `TriggerEvent` path; guarded by `kontinuance.github.webhook.enabled` (default false).
  Docs note: expose via Cloudflare Tunnel, never open ports.

## Phase 7: Polish & cross-cutting

- [ ] T025 [P] Rate-limit hardening: honor `X-RateLimit-Reset` / `Retry-After` with capped
  backoff; test the `403` secondary-limit path (no hot-loop). (FR-007)
- [ ] T026 [P] Secret-masking test: no token/step secret in any log, status description, or
  request across the suite. (SC-006)
- [ ] T027 [P] Restart/resume test: kill mid-window → cursor resumes, no miss/re-process. (SC-005)
- [ ] T028 Docs: README + quickstart pass; flag the `CursorStore` file impl as the persistence
  placeholder to fold into the future persistence feature.

---

## Dependencies & parallelization

- Phase 1 → Phase 2 → user-story phases. Within Phase 2, T004/T005 are `[P]` (distinct files).
- US1 (Phase 3) is the MVP and unblocks US2/US3; US2 and US3 are independent of each other.
- Test tasks (T008–T010, T016, T018–T020) are `[P]` within their phase and precede their
  implementation tasks (Constitution II — Red→Green).
- Phase 6 (webhook) and Phase 7 (polish) can proceed once US1 lands.

## Estimated MVP

Phases 1–3 (through T015) deliver the demonstrable value: **PRs get a gating Kontinuance
check, LAN unexposed.**
