# Feature Specification: Push/Notify Stream Source (poll selectable)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User: "Push source is good — maybe I want to continue allowing for polling too and have it
choosable." The live streams (`/api/runs/stream`, `/api/runs/{id}/logs/stream`) re-read the shared store on
a timer. This adds an **in-process push/notify source** so an in-process write (a server-triggered run, a
recorded log line) wakes the streams **immediately** instead of on the next poll — while keeping **polling
selectable** and retained as a fallback, so no topology loses updates.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Choose how streams learn about changes (Priority: P1)

An operator configures the stream mode: **poll** (the timer-only behavior, the default) or **push**
(in-process changes wake the stream at once, with the timer kept as a fallback).

**Why this priority**: Polling has inherent latency (up to a full interval) and constant idle re-reads.
Push removes the latency for in-process writes; keeping poll selectable means existing deployments and the
separate-writer topology are unaffected.

**Independent Test**: With `mode=poll` the streams behave exactly as before. With `mode=push` a change
recorded in-process appears without waiting for the poll interval.

**Acceptance Scenarios**:

1. **Given** `mode=poll` (default), **When** a client streams runs/logs, **Then** behavior is unchanged
   (timer-driven re-reads).
2. **Given** `mode=push`, **When** a run is recorded (or a log line appended) in-process after a client
   subscribes, **Then** it reaches the stream promptly via the change signal, not only on the next poll.

---

### User Story 2 - Push never loses out-of-process writes (Priority: P1)

Even in push mode, the poll timer keeps running as a fallback, so a run written by a **separate process**
(the `kontinuance-ci` writer) is still observed — push is a strictly-additive wakeup, not a replacement.

**Why this priority**: The `:server` and the CI writer can be different processes; a pure push source would
be blind to the other process's writes. Retaining polling keeps completeness for every topology.

**Independent Test**: In push mode, the poll still fires; a store change with no signal is still picked up on
the next timer tick.

**Acceptance Scenarios**:

1. **Given** `mode=push`, **When** the store changes with no in-process signal, **Then** the timer still
   surfaces it on the next tick (polling is retained, not disabled).

### Edge Cases

- **No subscriber**: a signal with no active collector is simply dropped (streams re-read current state on
  subscription); signals carry no data, so nothing is lost.
- **Signal during a re-read**: the stream holds one signal subscription for its whole lifetime, so a signal
  arriving mid-read is not missed; coalesced signals are harmless because each wakeup re-reads the full
  store.
- **Terminal completion (log tail)**: the log stream still drains and completes on a terminal run (so the
  SSE `end` fires), regardless of mode.

## Requirements *(mandatory)*

- **FR-001**: A `mode` setting MUST select **poll** (timer only; the default) or **push** for both live
  streams. `poll` MUST be byte-for-byte the prior behavior.
- **FR-002**: In **push** mode, an in-process write MUST wake the relevant stream immediately via a change
  signal, without waiting for the poll interval.
- **FR-003**: In **push** mode, the poll timer MUST remain active as a fallback so out-of-process writes are
  still observed — push is additive, never a replacement for polling.
- **FR-004**: The change signal MUST be fired automatically on every in-process store write (run recorded /
  log line appended), with no per-write-site code, so current and future writers are covered.
- **FR-005**: The stream wire contract (SSE events/shape, terminal `end`) MUST be unchanged; only the wakeup
  source changes. No new dependency; the web client is untouched.

## Success Criteria *(mandatory)*

- **SC-001**: `mode=poll` behaves exactly as before; `mode=push` delivers an in-process change with no poll
  wait (proven with a poll interval far larger than the test window).
- **SC-002**: In push mode the poll still runs; an unsignaled change is still surfaced by the timer.
- **SC-003**: Every in-process write signals automatically (store-decorator), covering runs and logs.
- **SC-004**: The SSE contract is unchanged (including terminal `end`); no new dependency; suites stay green.

## Assumptions

- **Push is only meaningful in-process.** A signal bus wakes streams for writes made in the same process
  (the server's own trigger/approval/log-recording paths). Cross-process writes are caught by the retained
  poll — this is why polling stays on in push mode and remains the safe default. A cross-process notify
  (DB `LISTEN`/broker) is a later feature behind the same seam.
- **Signals carry no data.** A woken stream re-reads the store (the source of truth), so the delta logic is
  identical to polling and coalesced signals are harmless — the store, not the signal, is authoritative.
