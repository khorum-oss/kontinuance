# Tasks: Durable Step Logs

**Feature**: 018-step-logs | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Input**: [plan.md](./plan.md), [spec.md](./spec.md), [contracts/logs-api.md](./contracts/logs-api.md)

No new dependency. `[P]` = parallelizable.

## Phase 1: Foundational — capture seam (blocking)

- [ ] T001 [P] Engine: add optional `logSink: LogSink? = null` to `PipelineEngine.run()`
  (`engine/.../execution/PipelineEngine.kt`) and use it in `DefaultPipelineEngine.run()`
  (`…/DefaultPipelineEngine.kt`) — `val sink = logSink ?: this.logSink`, thread into the per-run
  `StepRunner`. Default keeps existing behavior.
- [ ] T002 [P] Persistence: `RunLogStore` interface + `FileRunLogStore(dir)` (append one line per row to
  `<dir>/<sanitised-id>.log`; `read` returns ordered lines, empty when absent) + `InMemoryRunLogStore`, in
  `persistence/.../RunLogStore.kt`.
- [ ] T003 [P] Persistence test `RunLogStoreTest`: append→read order; per-id isolation; empty for
  unknown id; masked-line round-trip (store verbatim).

**Checkpoint**: the engine can be pointed at a per-run sink, and lines can be stored/read by id.

## Phase 2: User Story 1 — see a run's real output (P1) 🎯 MVP

- [ ] T004 [US1] Server: `RecordingLogSink(runId, store)` in `server/.../logs/RecordingLogSink.kt` —
  appends each emitted line to the `RunLogStore` and tees to stdout (container logs). Unit test
  `RecordingLogSinkTest`.
- [ ] T005 [US1] Server: `RunLogStore` bean under `<kontinuance.store>/logs` in `ServerConfig.kt`; in
  `RunLauncher.launch()` build a `RecordingLogSink(id, store)` and pass it to `engine.run(logSink = …)`
  (both the initial trigger and the approve-resume path).
- [ ] T006 [US1] Server: `RunLogController` — `GET /api/runs/{id}/logs` → `{"runId","lines":[…]}` (empty
  lines when none), raw-`ByteArray` JSON via `JsonView`, in `server/.../logs/`.
- [ ] T007 [US1] Server IT `RunLogsIT` (`@SpringBootTest`, real HTTP): trigger a run of an echo pipeline,
  then `GET /api/runs/{id}/logs` returns the step-prefixed lines; a secret echoed by a step is masked; an
  unknown id returns `{"lines":[]}`.
- [ ] T008 [US1] Web: `getRunLogs(id)` + `RunLog` type (`web/src/lib/api/client.ts` + `types.ts`);
  `RunDetail.svelte` renders the real lines (monospace, in order) with an explicit empty state, replacing
  the placeholder. Client unit test.

**Checkpoint**: a finished run shows its real, masked log in the UI.

## Phase 3: User Story 2 — live refresh while active (P2)

- [ ] T009 [US2] Web: in `web/src/routes/runs/[id]/+page.svelte`, fetch logs alongside the run; while the
  run status is non-terminal, re-fetch the log (and run) on a short interval, and stop at a terminal state.
- [ ] T010 [US2] E2E: extend `web/e2e/mock.ts` (route `/api/runs/{id}/logs`) + a run-detail test in
  `app.spec.ts` asserting the log lines render (and the empty state); keep existing suites green.

## Phase 4: Polish & verification

- [ ] T011 Docs: update `docs/getting-started.md` (drop "no step logs / presentational log view" from
  limitations) and `docs/running.md`/roadmap note as needed.
- [ ] T012 Verify: `:engine:test :persistence:test :server:test :*:detekt -Pdependency.env=public`; web
  `check` + `test:unit` + `test:e2e`. Confirm `verification-metadata.xml` unchanged and no new dependency.

## Dependencies & MVP

- T001–T003 block Phase 2. T004→T005→T006→T007 in order; T008 after T006. US2 (T009/T010) builds on T008.
- **MVP = Phase 1 + Phase 2** (recorded logs end-to-end). US2 adds live refresh. Live SSE/WebSocket log-tail
  and per-step grouping/pagination are follow-ups (not in this feature).
