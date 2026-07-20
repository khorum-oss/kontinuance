# Implementation Plan: Durable Step Logs

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/018-step-logs/spec.md`

## Summary

Capture the masked, `[step] `-prefixed lines the engine already streams to its `LogSink` and record them per
run, then serve and render them. Four small, layered changes: (engine) let a single `run()` invocation
override the log sink; (persistence) a `RunLogStore` that appends/reads lines by run id; (server) a
`RecordingLogSink` that the `RunLauncher` binds to the run id and passes into `engine.run(logSink = …)`, plus
a `GET /api/runs/{id}/logs` endpoint; (web) the run-detail log panel renders the real lines and refreshes
while the run is active. No new dependency; existing execution, status, masking, and APIs unchanged. A live
push/SSE log-tail is a follow-up — the polling refresh gives near-live behavior on the same stored lines.

## Technical Context

**Language/Version**: Kotlin/JDK 21 (engine, persistence, server) + Svelte 5/TypeScript (web).

**Primary Dependencies**: none new — the existing `LogSink` seam, the file-backed store pattern, WebFlux, and
the browser `fetch`.

**Storage**: file-backed per-run log (`<store>/logs/<id>.log`, one masked line per row), alongside the run
store.

**Testing**: engine unit test (per-invocation sink override reaches the executors); persistence
`FileRunLogStore` append/read/isolation; server IT (`@SpringBootTest` — trigger→logs round-trip) + a
`RecordingLogSink` unit test; web client unit test + E2E (run detail shows recorded lines / empty state).

**Constraints**: masking parity (store only what the sink emits — already masked); no change to run status or
existing endpoints; no new dependency; detekt/Kover green.

**Scale/Scope**: ~2 files in `persistence`, ~1 edit in `engine` (interface + impl), ~3 files in `server`,
~3 in `web`; plus tests.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive. `run()` gains an **optional** `logSink`
  override (defaulted), so existing callers (CI service, tests) are unchanged; the new `/api/runs/{id}/logs`
  is additive surface.
- **II. Test-First & Integration-Verified**: PASS — the persistence boundary and the server round-trip
  (trigger a real run, read its logs over HTTP) are integration-tested; masking parity is asserted.
- **III. Quality Gates**: PASS — detekt + Kover across the touched JVM modules; web check + unit + E2E.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency; `verification-metadata.xml` untouched.

No violations → Complexity Tracking empty.

## Project Structure

```text
engine/.../execution/PipelineEngine.kt        # EDIT — run() gains optional `logSink: LogSink? = null`
engine/.../execution/DefaultPipelineEngine.kt # EDIT — use the override (else the engine's default) per run

persistence/.../RunLogStore.kt                # NEW — interface + FileRunLogStore + InMemoryRunLogStore
persistence/.../RunLogStoreTest.kt (test)     # NEW — append/read/isolation/empty

server/.../logs/RecordingLogSink.kt           # NEW — append each emitted line to RunLogStore under a run id
server/.../logs/RunLogController.kt            # NEW — GET /api/runs/{id}/logs → {"lines":[…]}
server/.../trigger/RunLauncher.kt             # EDIT — build a RecordingLogSink(id) and pass to engine.run()
server/.../ServerConfig.kt                    # EDIT — RunLogStore bean (under <store>/logs)
server/.../ (tests)                           # NEW — RunLogsIT (trigger→logs), RecordingLogSinkTest

web/src/lib/api/client.ts + types.ts          # EDIT — getRunLogs(id): string[]; RunLog type
web/src/lib/screens/RunDetail.svelte          # EDIT — render real lines + empty state (replace placeholder)
web/src/routes/runs/[id]/+page.svelte         # EDIT — fetch logs; poll while active, stop at terminal
web/ (tests)                                   # EDIT — client.test.ts + e2e mock/app.spec
```

**Structure Decision**: Reuse the existing `LogSink` seam rather than adding a new engine concept — the only
engine change is letting one `run()` pick its sink, which the server uses to route a run's output into a
per-run file. The store mirrors the existing file-run-store pattern so a DB backend can replace it later
behind the same interface.

## Complexity Tracking

> No Constitution Check violations — no entries.
