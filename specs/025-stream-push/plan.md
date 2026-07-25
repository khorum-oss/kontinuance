# Implementation Plan: Push/Notify Stream Source (poll selectable)

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/025-stream-push/spec.md`

## Summary

Add an in-process `RunChangeNotifier` signal bus and a `kontinuance.stream.mode` setting (`poll` default |
`push`). Both live streams re-read the store off a `streamTriggers(mode, pollIntervalMs, signals)` flow: in
`poll` mode a bare timer (unchanged behavior), in `push` mode the timer **merged** with the notifier's
signals (a single lifetime subscription, so no missed signals) — instant in-process wakeups with the timer
kept as a fallback. Writes signal automatically via `NotifyingRunStore`/`NotifyingRunLogStore` decorators
wired in `ServerConfig`, so every writer (trigger/approval/log-recording) and any future one signals with no
per-site code. The SSE contract is unchanged (including the log tail's terminal `end`, preserved via
`transformWhile`). No new dependency; the web client is untouched.

## Technical Context

**Language/Version**: Kotlin/JDK 21 + Spring Boot WebFlux (`:server`), kotlinx-coroutines Flow.

**Primary Dependencies**: none new — `MutableSharedFlow`, existing stores.

**Storage**: none new (decorates the existing stores).

**Testing**: `StreamNotifierTest` (mode parsing, signal delivery, decorator delegation); `RunStreamPushIT`
and `RunLogStreamPushIT` (`@SpringBootTest(RANDOM_PORT)`, `mode=push`, **60s** poll so only the signal can
deliver within the window — a real-HTTP proof of the whole decorator→notifier→stream chain). The existing
poll ITs (`RunStreamIT`, `RunLogStreamIT`) stay green unchanged (poll is the default).

**Constraints**: `poll` byte-for-byte unchanged; push additive (timer retained); signals fire on every
in-process write via a decorator; SSE contract + terminal `end` unchanged; no new dependency.

**Scale/Scope**: 5 new `server/stream/*` files (`StreamMode`, `RunChangeNotifier`, `streamTriggers`, the two
decorators); edit `RunStream`, `RunLogStream`, `ServerConfig`; tests + docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — the SSE contract is unchanged; only the internal
  wakeup source changes, gated behind an opt-in `mode` (default `poll` = prior behavior).
- **II. Test-First & Integration-Verified**: PASS — push delivery is proven end-to-end over real HTTP with a
  poll interval far larger than the test window; the seam is unit-tested.
- **III. Quality Gates**: PASS — detekt/Kover on `:server`.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency.

No violations → Complexity Tracking empty.

## Project Structure

```text
server/.../stream/StreamMode.kt              # NEW — poll | push (from(raw), default poll)
server/.../stream/RunChangeNotifier.kt        # NEW — @Component signal bus (runs + per-run log signals)
server/.../stream/StreamTriggers.kt           # NEW — streamTriggers(mode, pollMs, signals): Flow<Unit>
server/.../stream/NotifyingRunStore.kt        # NEW — decorator: signal after record
server/.../stream/NotifyingRunLogStore.kt     # NEW — decorator: signal after append
server/.../RunStream.kt                        # EDIT — trigger-driven; inject notifier + mode
server/.../logs/RunLogStream.kt                # EDIT — trigger-driven (transformWhile keeps terminal end)
server/.../ServerConfig.kt                     # EDIT — wrap the file stores in the notifying decorators
server/.../stream/StreamNotifierTest.kt (test)         # NEW — mode/signal/decorator units
server/.../stream/RunStreamPushIT.kt (test)            # NEW — push delivers a run (60s poll)
server/.../stream/RunLogStreamPushIT.kt (test)         # NEW — push delivers a log line (60s poll)
docs/getting-started.md, docs/roadmap.md       # EDIT — mode=poll|push; push retires poll latency, poll kept
```

**Structure Decision**: Keep the store as the source of truth and make push a wakeup-only signal, so the
delta logic is shared by both modes and coalesced/lost signals are harmless. Decorate the stores (rather
than editing write sites) so signalling is automatic and total. Retain the poll timer in push mode so the
separate-writer topology never loses updates — which is also why `poll` stays the default and remains
selectable, exactly as requested.

## Complexity Tracking

> No Constitution Check violations — no entries.
