# Feature Specification: GitHub Event Source & External-CI Integration

**Feature Branch**: `003-github-event-source`

**Created**: 2026-07-12

**Status**: Implemented (US1 MVP — engine-only; US2/US3 deferred, see tasks.md)

**Input**: User description: "Let Kontinuance act as an external CI for GitHub: detect new PRs and pushes, run the matching pipeline via the v0 engine, and report the outcome back to GitHub as a commit status / check so a required check gates merges. Poll GitHub by default (no inbound exposure); a webhook via Cloudflare Tunnel is an optional lower-latency mode later. This is the piece that lets Kontinuance replace GitHub Actions for the Hestia/Relikquary delivery flow without exposing the LAN."

## Context

Feature `001-pipeline-foundation` executes a pipeline (stages → steps) in-process and
reports a sealed-class status; `002-typed-steps` adds typed build steps. Neither is
*triggered* by anything external — a human invokes a run. This feature adds the **event
source** (the third plane from the platform overview: orchestrator / agent / **event
source**) so a repository event *starts* a run, and adds the **outbound reporting** that
makes Kontinuance a first-class check on a GitHub PR — the standard external-CI pattern
(Jenkins/CircleCI-style) rather than GitHub Actions.

Architectural stance baked into this spec: **poll-first**. Kontinuance runs on a private
LAN (the Hestia Mini); polling the GitHub API is *outbound only*, so nothing about the
host is exposed. A webhook mode (behind a Cloudflare Tunnel) is offered as an optional
latency optimization with identical downstream behavior — never a prerequisite.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A PR triggers a run and its outcome posts back as a check (Priority: P1)

A developer opens (or pushes to) a pull request on a configured GitHub repository.
Kontinuance detects the PR's head commit, starts the repository's pipeline against that
commit, and reports the result back to GitHub as a **commit status / check run** on that
SHA — `pending` while it runs, then `success` or `failure`.

**Why this priority**: This is the irreducible external-CI loop — turn a GitHub event
into a run and surface the result where the PR lives. Everything else (gating, push
triggers, webhooks) builds on it. It is the MVP on its own: with just this, Kontinuance
already shows a green/red check on PRs.

**Independent Test**: Point Kontinuance at a test repo + a trivial pipeline; open a PR;
assert (against a mocked GitHub API) that a `pending` status is created on the head SHA,
the pipeline runs, and the status transitions to `success` (all steps exit 0) or
`failure` (a step exits non-zero) with a link/description identifying the run.

**Acceptance Scenarios**:

1. **Given** a configured repo with a pipeline and an open PR whose pipeline passes,
   **When** the event source observes the PR head SHA, **Then** a `pending` status is
   posted on that SHA, the pipeline runs to completion, and the status becomes `success`.
2. **Given** the same setup but a pipeline whose a step exits non-zero, **When** it runs,
   **Then** the status becomes `failure` and its description names the failing step.
3. **Given** a PR that receives a new push (new head SHA), **When** the event source
   observes it, **Then** a fresh run starts for the new SHA and reports on the new SHA
   (the prior SHA's status is left as-is).
4. **Given** the GitHub API is unreachable when reporting the outcome, **When** the run
   finishes, **Then** the reporter retries with backoff and the run is not lost.

---

### User Story 2 - A required check gates the merge (Priority: P2)

A maintainer configures branch protection so the Kontinuance check is **required**. A PR
cannot merge until Kontinuance reports `success`; a `failure` (or a still-`pending` run)
blocks the merge in the GitHub UI.

**Why this priority**: This is what makes the check *enforce* the gate ("trigger on PR
and wait for it to finish"). It needs Story 1's status reporting but is a distinct,
separately valuable capability (the gate) and depends on repo configuration, so it sits
behind the core loop.

**Independent Test**: With a required-check ruleset naming the Kontinuance check, assert
(against the mocked API) that the mergeable state reflects the check — blocked while
`pending`/`failure`, mergeable on `success`. The check **context/name is stable** so the
required-check rule keeps matching across runs (Constitution: stable public contract).

**Acceptance Scenarios**:

1. **Given** a required-check rule naming the Kontinuance context, **When** a run is
   `pending` or `failure`, **Then** the PR is reported non-mergeable.
2. **Given** the same rule, **When** the run reports `success`, **Then** the PR becomes
   mergeable.
3. **Given** two runs for the same PR over time, **When** each reports, **Then** both use
   the **same check context string**, so the branch-protection rule matches without
   reconfiguration.

---

### User Story 3 - Push-to-main triggers delivery; manual re-run is available (Priority: P3)

Beyond PR checks, a push (merge) to a tracked branch (e.g. `main`) triggers the
repository's *delivery* pipeline (build → publish → deploy), and an operator can manually
(re-)trigger a run for a given ref without a new GitHub event.

**Why this priority**: PR gating (Stories 1–2) proves the integration; production
delivery on merge and a manual escape hatch are what make it operationally complete for
the Hestia flow, but they reuse the same event-source + run plumbing.

**Independent Test**: Simulate a push event to `main` and assert the configured delivery
pipeline starts for the pushed SHA; invoke the manual trigger for an arbitrary ref and
assert a run starts and reports (or, for a non-PR ref, records the outcome without a PR
check).

**Acceptance Scenarios**:

1. **Given** a repo configured with a delivery pipeline on `main`, **When** a push to
   `main` is observed, **Then** the delivery pipeline runs for the pushed SHA.
2. **Given** any resolvable ref, **When** an operator triggers a manual run, **Then** a
   run starts for that ref's SHA and its outcome is recorded.
3. **Given** the same GitHub event is observed twice (poll overlap or webhook re-delivery),
   **When** processed, **Then** only one run is started for that (repo, SHA, pipeline)
   — triggers are idempotent.

---

### Edge Cases

- **Duplicate/late delivery**: the poller re-lists an event already handled, or a webhook
  is re-sent → dedup by (repo, head SHA, pipeline) so no double run.
- **Superseded PR head**: a new push arrives mid-run → the in-flight run for the old SHA
  is allowed to finish (or is cancelled per policy) and never reports on the new SHA.
- **Rate limiting**: GitHub returns `403`/secondary-rate-limit → the poller honors
  `Retry-After` / resets and backs off; it MUST NOT hot-loop.
- **Auth failure / expired token**: reporting/polling 401s → surfaced as a clear
  operator-visible error; runs are not silently dropped.
- **Poller downtime**: Kontinuance is offline for a window → on restart it resumes from a
  durable cursor and does not miss or infinitely re-process events.
- **No pipeline for the repo/event**: an event arrives for an unconfigured repo → ignored,
  logged, no status posted.
- **Secret leakage**: tokens and step secrets MUST NOT appear in logs, statuses, or check
  output (reuses 001 masking; extends it to the reporter).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a **poll-based event source** that periodically
  queries GitHub (outbound only) for new PR head commits and pushes on configured
  repositories, requiring no inbound network exposure.
- **FR-002**: The system MUST start a pipeline **run** (via the 001 engine) for an
  observed event, resolving which pipeline to run from repository configuration and the
  event type (PR vs push).
- **FR-003**: The system MUST report run outcome to GitHub as a **commit status or check
  run** on the event's head SHA, transitioning `pending → success | failure`, with a
  human-readable description and (where available) a link to the run.
- **FR-004**: The check **context/name MUST be stable** across runs so a branch-protection
  required-check rule matches without reconfiguration (Constitution I — stable contract).
- **FR-005**: Trigger handling MUST be **idempotent**: the same (repo, head SHA, pipeline)
  MUST NOT start more than one concurrent run, whether an event is re-observed by polling
  overlap or webhook re-delivery.
- **FR-006**: The poller MUST persist a **durable cursor / last-seen marker** per repo so
  it resumes after a restart without missing or re-processing events. [Persistence
  mechanism deferred to the persistence feature; this feature MAY use a minimal durable
  store or file for the cursor — see Assumptions.]
- **FR-007**: The system MUST **retry outbound GitHub calls with backoff** on transient
  failures and honor GitHub rate-limit / `Retry-After` responses without hot-looping.
- **FR-008**: The system MUST support a **push-to-branch trigger** (e.g. `main`) that runs
  a configured *delivery* pipeline for the pushed SHA (US3).
- **FR-009**: The system MUST support a **manual trigger** for an arbitrary resolvable ref.
- **FR-010**: GitHub credentials (App private key or token) MUST be supplied via
  environment / untracked config and MUST NEVER be committed or logged (Constitution V).
- **FR-011**: All GitHub API interaction MUST be behind an interface that is
  **integration-tested against a mock GitHub API** (Constitution II — every external
  integration covered by integration tests; e.g. WireMock/Testcontainers), and unit tests
  MUST cover event→pipeline resolution, dedup, and status mapping without the network.
- **FR-012**: The event source MUST be a **distinct concern** from execution (001) and
  reporting — designed as separate components in one process now, separable later
  (overview: orchestrator/agent/event-source split from day one).
- **FR-013**: An **optional webhook receiver** MAY be provided as an alternative trigger
  mode with identical downstream behavior (same run + reporting path); it is **not
  required** and MUST NOT be the default. When used, it is expected to be exposed via a
  Cloudflare Tunnel rather than open ports, and MUST verify webhook signatures.
- **FR-014**: Secrets and tokens MUST be masked in logs, status descriptions, and check
  output (extends 001 masking to the reporter).

### Key Entities

- **RepositoryBinding**: config linking a GitHub repo (owner/name) to the pipeline(s) it
  runs, per event type (PR check pipeline, push/delivery pipeline), and the check context
  name.
- **TriggerEvent**: a normalized event (repo, kind = PR | PUSH | MANUAL, head SHA, ref,
  PR number if any) produced by the poller/webhook, consumed by the runner.
- **RunReport**: the outbound status/check payload (state, context, description, target
  URL) mapped from a 001 `Run` outcome.
- **PollCursor**: durable per-repo marker recording what has been observed/handled.
- **GitHubConnection**: the authenticated client (App/token) + rate-limit state; the sole
  seam mocked in integration tests.

## Success Criteria *(mandatory)*

- **SC-001**: Opening a PR on a configured repo results in a `pending` check appearing on
  the head SHA within one poll interval, and a terminal `success`/`failure` within one
  poll interval of the run finishing — with **no inbound connection to the host**.
- **SC-002**: A required-check rule naming the Kontinuance context blocks merge until the
  run is `success`, using a check context that is byte-stable across runs.
- **SC-003**: Re-observing an event (poll overlap or webhook re-delivery) never starts a
  second run for the same (repo, SHA, pipeline).
- **SC-004**: The full GitHub interaction (poll → status create → status update) passes an
  **integration test against a mock GitHub API**, and no test requires real GitHub or
  network access.
- **SC-005**: Killing and restarting Kontinuance mid-window loses no event and re-processes
  none (cursor resumes correctly).
- **SC-006**: No token or step secret appears in any log line, status description, or check
  output across the test suite.

## Assumptions & Dependencies

- Builds on **001** (engine/run/status) and its secret-masking; **does not** change the
  execution loop. Typed steps (**002**) are orthogonal — the delivery pipeline will use
  them but this feature is step-agnostic.
- **Persistence** is a separate future feature. This feature needs only a *minimal* durable
  cursor; it MAY use a lightweight store (file or embedded DB) as a placeholder, to be
  folded into the persistence feature later — flagged so it isn't a hidden coupling.
- Assumes a GitHub **App or token** with permission to read PRs/commits and write commit
  statuses / checks. GitHub App is preferred (fine-grained scopes, Checks API, higher rate
  limits) but a PAT is acceptable for v1.
- Branch-protection / required-check configuration is done **in GitHub** by the operator;
  Kontinuance only guarantees a stable check context to match against.

## Out of Scope

- Persistent run history / UI (future features).
- Runner isolation (Docker/k8s) — still in-process per 001/002.
- The delivery pipeline's *contents* (build/publish/render/deploy/UAT/prod-gate) — those
  are pipeline definitions + typed steps, specified/authored separately (the Hestia CD
  pipeline). This feature only *triggers* and *reports*.
- Multi-SCM support (GitLab/Gitea/Bitbucket) — GitHub only for v1, though the event-source
  interface should not preclude it.
