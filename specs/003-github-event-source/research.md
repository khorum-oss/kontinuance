# Phase 0 Research: GitHub Event Source & External-CI Integration

Decisions that de-risk the plan. Each records the choice, rationale, and rejected options.

## D1 — Trigger transport: poll vs webhook

**Decision**: **Poll GitHub by default** (outbound only). Provide an optional
signature-verified webhook receiver behind a Cloudflare Tunnel as a *later* latency mode.

**Rationale**: Kontinuance runs on a private LAN (Hestia Mini). Polling needs **no inbound
exposure** — the single biggest security win for a homelab. Latency (one poll interval,
30–60s) is invisible for PR checks. Webhooks are strictly a latency optimization and share
the entire downstream (resolve → run → report), so adding them later is additive, not a
rewrite.

**Rejected**: webhook-first (needs a public endpoint / tunnel + secret rotation from day
one); GitHub Actions self-hosted runner (defeats the point — the goal is to *replace*
Actions and keep CD on-stack).

## D2 — Status transport: Commit Status API vs Checks API

**Decision**: **Commit Status API** for v1 (`POST /repos/{o}/{r}/statuses/{sha}` with
`state` ∈ pending|success|failure|error, a stable `context`, `description`, `target_url`).

**Rationale**: works with a **PAT** (no GitHub App required), and **required status checks**
in branch protection match on the `context` string — exactly the gate we need (US2).
Minimal surface, easy to mock. The Checks API is richer (check runs, annotations,
re-run buttons) but **requires a GitHub App** and more surface; defer it as an enhancement.

**Rejected (deferred)**: Checks API — revisit once a GitHub App exists and annotations/step
detail are wanted.

## D3 — GitHub client library

**Decision**: a **thin internal `GitHubClient` interface** over an HTTP client
(`WebClient`/OkHttp), exposing only the calls we use (list open PRs, get ref/commit, create
status). A concrete `RestGitHubClient` implements it.

**Rationale**: the interface is the **one seam we mock** for Constitution-II integration
tests; keeping it thin means the WireMock fixtures are small and we don't pull a large
dependency (`org.kohsuke:github-api`) or its transitive verification-metadata burden for a
handful of endpoints.

**Rejected**: `org.kohsuke:github-api` (heavier, more to verify, harder to fault-inject for
rate-limit tests); raw ad-hoc HTTP scattered in callers (unmockable, violates II).

## D4 — Poll strategy & rate-limit friendliness

**Decision**: per repo, list **open PRs** and compare each head SHA against the `PollCursor`;
also list recent pushes on tracked branches. Use **ETag / `If-None-Match`** and conditional
requests so unchanged polls cost **0** rate-limit budget; honor `X-RateLimit-Reset` and
`Retry-After` with capped exponential backoff.

**Rationale**: conditional requests make polling nearly free at rest; explicit reset/backoff
prevents secondary-rate-limit hot-loops (an edge case in the spec).

**Rejected**: unconditional full listing every interval (burns budget); tight fixed-interval
retry on 403 (triggers secondary limits).

## D5 — Idempotency & the cursor

**Decision**: dedup key = **(repo, head SHA, pipeline)**; a run is started only if that key
is not already in-flight/handled. The **`PollCursor`** persists the last-handled marker per
repo so a restart resumes without missing or re-processing.

**Rationale**: polling overlap and webhook re-delivery both re-present events (spec edge
cases); a content-addressed key (head SHA) is the natural idempotency token.

**Rejected**: dedup by event id/timestamp only (misses re-pushes to the same PR; a new push
= new SHA = legitimately a new run).

## D6 — Cursor storage (placeholder pending persistence feature)

**Decision**: a small **`CursorStore` interface** with a file-backed (or embedded H2) impl
for now, **explicitly flagged** to fold into the future persistence feature. No run history.

**Rationale**: this feature needs only a tiny durable marker; introducing full persistence
here would over-couple. The interface keeps the swap trivial later.

**Rejected**: reaching for a full DB now (premature; that's a separate feature); in-memory
only (loses the cursor across restarts — fails SC-005).

## D7 — Auth & secrets

**Decision**: credentials (PAT now; GitHub App key when Checks API lands) via **environment
/ untracked config**; masked in all output. A single `GitHubConnection` holds the client +
rate-limit state.

**Rationale**: Constitution V — never commit/log secrets. One connection object localizes
auth + limits and is the mock boundary.

## Open questions for Phase 1

- Exact `context` string convention (e.g. `kontinuance/<pipeline>`), fixed by a test.
- Cancel-vs-let-finish policy for a superseded PR head (spec edge case) — default: let the
  old run finish, only report on its own SHA.
