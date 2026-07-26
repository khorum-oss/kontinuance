# Tasks: Cancel a Running Run from the UI

**Feature**: 028-cancel-run | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Engine

- [X] T001 `PipelineEngine.run(..., runId: RunId? = null)` + `DefaultPipelineEngine` uses the caller id or
  generates one (existing callers unchanged).
- [X] T002 `CancellationTest`: cancel a caller-provided id through `PipelineEngine.default` — ends
  `Cancelled`, `run.id` is the caller id.

## Server

- [X] T003 `RunLauncher`: launch with `runId = RunId(id)` so the run is addressable by the server id.
- [X] T004 `RunCanceller`: `store.get(id)` null → `NOT_FOUND`; not actively running → `NOT_ACTIVE`; else
  `engine.cancel(RunId(id))` → `CANCELLING`.
- [X] T005 `CancelController`: `POST /api/runs/{id}/cancel` → `200 {"status":"cancelling"}` / `409` / `404`.
- [X] T006 `RunCancellerTest` (fake engine): running→CANCELLING (id routed), terminal/waiting→NOT_ACTIVE,
  unknown→NOT_FOUND. `CancelControllerIT`: 200/409/404 over real HTTP.

## Web

- [X] T007 `client.ts`: `cancelRun(id)` (postRun 'cancel').
- [X] T008 `RunDetail.svelte`: **CANCEL RUN** control while running/pending (hidden for terminal/waiting);
  `cancelling`/`cancelError` states.
- [X] T009 `runs/[id]/+page.svelte`: `cancel()` wiring; the live tail settles the final `Cancelled` status.
- [X] T010 `e2e/mock.ts` `mockCancelableRun` + `app.spec.ts` cancel-flow E2E (running → CANCEL RUN →
  Cancelled).

## Docs

- [X] T011 `docs/getting-started.md`: cancel a running run from the UI (028).

## Verification

- [X] T012 `:engine:test :engine:detekt :server:test :server:detekt -Pdependency.env=public` green; web
  `svelte-check`, Vitest, Playwright green.
