# Implementation Plan: Cancel a Running Run from the UI

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/028-cancel-run/spec.md`

## Summary

Add an optional `runId` to `engine.run` (default `null` → generated, so existing callers are unchanged) so
`RunLauncher` can launch each run with the server's own id as the engine run id. A `RunCanceller` then
addresses it via the existing `engine.cancel(RunId)`: for an actively-running run it cancels (the engine
terminates the step and the run ends `Cancelled`, which `RunLauncher` records), for a terminal or gated run
it reports "not active", and for an unknown id "not found". `CancelController` maps those to
`200`/`409`/`404` on `POST /api/runs/{id}/cancel`. The run-detail view gains a **CANCEL RUN** control shown
only while the run is running/pending, wired through `api.cancelRun`. Additive and dependency-free.

## Technical Context

**Language/Version**: Kotlin/JDK 21 (`:engine`, `:server` Spring WebFlux) + Svelte 5 (`web`).

**Primary Dependencies**: none new.

**Storage**: none new (reads the run store for status gating).

**Testing**: engine `CancellationTest` gains a caller-provided-id cancel over the default engine;
`RunCancellerTest` (fake engine) covers status gating + id routing; `CancelControllerIT`
(`@SpringBootTest(RANDOM_PORT)`) covers the 200/409/404 mapping. Web: `svelte-check`, Vitest, and a
Playwright cancel-flow E2E (running → CANCEL RUN → Cancelled).

**Constraints**: existing engine-run behavior unchanged when no id is supplied; cancel only for
actively-running runs; auth-gated like other write actions; no new dependency.

**Scale/Scope**: engine `PipelineEngine`/`DefaultPipelineEngine` (optional `runId`); server `RunLauncher`
(pass the id) + `RunCanceller` + `CancelController` (new); web `client.ts` (`cancelRun`),
`RunDetail.svelte` (control), `runs/[id]/+page.svelte` (wiring); tests + docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive endpoint + an optional engine-run
  parameter defaulting to prior behavior; existing callers and the `/api/runs` contract are unchanged.
- **II. Test-First & Integration-Verified**: PASS — the engine cancel-by-caller-id is tested, the gating is
  unit-tested with a fake engine, the HTTP mapping is integration-tested, and the UI flow is E2E-tested.
- **III. Quality Gates**: PASS — detekt/Kover on `:engine`/`:server`; svelte-check + Vitest + Playwright.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency.

No violations → Complexity Tracking empty.

## Project Structure

```text
engine/.../execution/PipelineEngine.kt            # EDIT — run(..., runId: RunId? = null)
engine/.../execution/DefaultPipelineEngine.kt      # EDIT — use the caller id or generate one
engine/.../execution/CancellationTest.kt (test)    # EDIT — cancel a caller-provided id via the default engine
server/.../trigger/RunLauncher.kt                  # EDIT — launch with runId = RunId(id)
server/.../trigger/RunCanceller.kt                 # NEW — status gating + engine.cancel(RunId(id))
server/.../trigger/CancelController.kt             # NEW — POST /api/runs/{id}/cancel → 200/409/404
server/.../trigger/RunCancellerTest.kt (test)      # NEW — running→cancel, terminal/waiting→not-active, unknown→not-found
server/.../trigger/CancelControllerIT.kt (test)    # NEW — HTTP 200/409/404 mapping
web/src/lib/api/client.ts                           # EDIT — cancelRun(id) (postRun 'cancel')
web/src/lib/screens/RunDetail.svelte                # EDIT — CANCEL RUN control while running/pending
web/src/routes/runs/[id]/+page.svelte               # EDIT — cancel() wiring
web/e2e/mock.ts + app.spec.ts                       # EDIT — mockCancelableRun + cancel E2E
docs/getting-started.md                             # EDIT — cancel a running run from the UI (028)
```

**Structure Decision**: Reuse the engine's existing `cancel(RunId)` rather than inventing a new cancel path;
the only gap was id correlation, closed by the optional `runId` on `run`. Keep gating (which runs are
cancellable) in `RunCanceller` so the controller stays a thin transport, mirroring the approval path.

## Complexity Tracking

> No Constitution Check violations — no entries.
