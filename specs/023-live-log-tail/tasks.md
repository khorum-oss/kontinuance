# Tasks: Live Log Tail (SSE)

**Feature**: 023-live-log-tail | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Server

- [X] T001 `RunLogStream` (`@Component`): cold `Flow<String>` over `RunLogStore` + `RunStore`; emit every
  recorded line by index, poll on `kontinuance.stream.poll-interval-ms`, drain and complete once the run's
  status is terminal (success/fail/cancel/timed/skip). Blocking reads on `Dispatchers.IO` (FR-001/002/004).
- [X] T002 `RunLogStreamController`: `GET /api/runs/{id}/logs/stream` (`text/event-stream`) → `log` event per
  line + terminal `end` event on normal completion via `onCompletion` (FR-001/003).
- [X] T003 `RunLogStreamIT` (`@SpringBootTest(RANDOM_PORT)`, fast poll): seed `@Primary` log store (2 lines)
  + `@Primary` terminal run; GET the stream and assert both lines arrive over real HTTP.

## Web

- [X] T004 `live.ts`: add `logStream(id)` readable store — EventSource on `/api/runs/{id}/logs/stream`;
  accumulate `log` lines, reset on `open`, mark `done` + close on `end`; inert without `EventSource` (FR-005).
- [X] T005 `live.test.ts`: `logStream` unit tests — accumulate/order + url, `end` → done+closed, `open` reset,
  teardown closes, inert without EventSource.
- [X] T006 `runs/[id]/+page.svelte`: subscribe to `logStream(id)` in an effect; drop the 1.5s interval poll
  and the one-shot log fetch from `load()`; re-fetch the run summary on the terminal signal (FR-005).
- [X] T007 `e2e/mock.ts`: `mockLogStream(lines)` SSE route (`log` events + `end`); wire into `mockApi`
  (recorded lines) and `mockWaitingRun` (empty). Existing `/logs$` one-shot route kept.

## Docs

- [X] T008 `docs/getting-started.md`, `docs/roadmap.md`: logs are a live SSE tail (023), not client polling.

## Verification

- [X] T009 `:server:test :server:detekt -Pdependency.env=public` green; web `svelte-check`, Vitest,
  Playwright green.
