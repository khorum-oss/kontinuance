# Implementation Plan: Live Log Tail (SSE)

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-live-log-tail/spec.md`

## Summary

Add a per-run live log tail over Server-Sent Events. A `RunLogStream` component polls the 018 `RunLogStore`
behind a cold `Flow<String>` — emitting every already-recorded line, then each new line as it lands — and
completes once the run's `RunStore` status is terminal. A `RunLogStreamController` exposes it at
`GET /api/runs/{id}/logs/stream` as `text/event-stream`: one `log` event per line, then a terminal `end`
event on normal completion. This mirrors the existing `RunStream` / `RunStreamController` (`/api/runs/stream`)
exactly. On the web, `live.ts` gains a `logStream(id)` readable store (EventSource), and the run-detail page
subscribes to it in place of its 1.5s interval polling, re-fetching the run summary on the terminal signal.
The one-shot `GET /api/runs/{id}/logs` (018) stays. No new dependency.

## Technical Context

**Language/Version**: Kotlin/JDK 21 + Spring Boot WebFlux (`:server`) + Svelte 5 (`web`).

**Primary Dependencies**: none new — `RunLogStore`, `RunStore`, kotlinx-coroutines `Flow`, EventSource.

**Storage**: none new (polls the existing log + run stores).

**Testing**: `RunLogStreamIT` (`@SpringBootTest(RANDOM_PORT)`, seeded log + terminal run, fast poll) asserts
the recorded lines stream over a real HTTP round-trip. Web: `live.ts` `logStream` unit tests (mock
EventSource), `svelte-check`, Vitest, Playwright (run-detail E2E now driven by the mocked SSE log stream).

**Constraints**: cold/structured `Flow` so a disconnect cancels the poll (FR-004); SSE only (WebSocket out of
scope); one-shot 018 endpoint preserved; no new dependency.

**Scale/Scope**: two new server files + one IT; `live.ts` + run-detail page + e2e mock + unit tests + docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive endpoint; the 018 `/logs` contract and the
  `/api/deploy` etc. contracts are untouched.
- **II. Test-First & Integration-Verified**: PASS — the tail is integration-tested over the real HTTP
  boundary with a seeded terminal run; the web store is unit-tested and the view is E2E-tested.
- **III. Quality Gates**: PASS — detekt/Kover on `:server`; svelte-check + Vitest + Playwright on `web`.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency; `verification-metadata.xml` untouched.

No violations → Complexity Tracking empty.

## Project Structure

```text
server/.../logs/RunLogStream.kt                 # NEW — cold Flow<String>; polls RunLogStore, ends on terminal RunStore status
server/.../logs/RunLogStreamController.kt        # NEW — GET /api/runs/{id}/logs/stream (SSE: log events + terminal end)
server/.../logs/RunLogStreamIT.kt (test)         # NEW — seeded terminal run → lines stream over real HTTP

web/src/lib/api/live.ts                           # EDIT — add logStream(id) readable store (EventSource)
web/src/lib/api/live.test.ts                      # EDIT — logStream unit tests (accumulate/end/reopen/inert)
web/src/routes/runs/[id]/+page.svelte             # EDIT — subscribe to logStream; drop the interval polling
web/e2e/mock.ts                                   # EDIT — mockLogStream SSE route; wired into mockApi + mockWaitingRun
docs/getting-started.md, docs/roadmap.md          # EDIT — logs are a live SSE tail (023), not client polling
```

**Structure Decision**: Mirror the existing run stream (`RunStream` + `RunStreamController`) one-to-one for
the per-run log tail, so the streaming model, cancellation semantics, and poll configuration
(`kontinuance.stream.poll-interval-ms`) are shared and familiar. The web `logStream` store mirrors the
existing `runStream` store. Only the run-detail page changes behaviorally (polling → stream).

## Complexity Tracking

> No Constitution Check violations — no entries.
