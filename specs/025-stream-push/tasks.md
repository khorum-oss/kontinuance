# Tasks: Push/Notify Stream Source (poll selectable)

**Feature**: 025-stream-push | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Seam

- [X] T001 `StreamMode` enum (`POLL`/`PUSH`) + `from(raw)` (default `POLL` for null/unknown).
- [X] T002 `RunChangeNotifier` (`@Component`): `MutableSharedFlow` run + per-run log signals (no replay,
  drop-oldest buffer); `signalRuns()`/`signalLog(id)` fire-and-forget; `runSignals()`/`logSignals(id)` flows.
- [X] T003 `streamTriggers(mode, pollMs, signals)`: emits once immediately (snapshot), then a timer in poll
  mode or `merge(timer, signals)` in push mode (one lifetime subscription → no missed signals).
- [X] T004 `NotifyingRunStore` / `NotifyingRunLogStore` decorators: signal after `record` / `append`; reads
  delegate untouched.

## Wiring

- [X] T005 `RunStream`: inject notifier + mode; drive the re-read loop from `streamTriggers` (dedup by id).
- [X] T006 `RunLogStream`: inject notifier + mode; drive from `streamTriggers` via `transformWhile` so the
  drain-then-stop terminal behavior (and the SSE `end`) is preserved.
- [X] T007 `ServerConfig`: wrap the file stores in the notifying decorators (every writer now signals).

## Tests

- [X] T008 `StreamNotifierTest`: mode parsing; `signalRuns`/`signalLog` wake a collector; `logSignals`
  filters by run id; both decorators delegate reads+writes.
- [X] T009 `RunStreamPushIT`: `mode=push`, 60s poll; a run recorded through the notifying store after
  subscription arrives via the signal (real HTTP).
- [X] T010 `RunLogStreamPushIT`: `mode=push`, 60s poll; a line appended after subscription arrives via the
  signal; the seeded run stays `Running` so the tail stays open.

## Docs

- [X] T011 `docs/getting-started.md`, `docs/roadmap.md`: `kontinuance.stream.mode=poll|push`; push removes
  in-process poll latency, poll retained as fallback and default.

## Verification

- [X] T012 `:server:test :server:detekt -Pdependency.env=public` green (poll ITs unchanged; push ITs pass);
  no new dependency.
