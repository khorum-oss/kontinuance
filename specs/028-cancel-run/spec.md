# Feature Specification: Cancel a Running Run from the UI

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User: "Cancel a running run from the UI." The engine already supports cancelling an in-flight run
(it terminates the step and ends the run `Cancelled`), but there was no way to reach it from the web UI.
This adds a **CANCEL RUN** control on the run-detail view and the `POST /api/runs/{id}/cancel` endpoint
behind it, correlating the server's run id with the engine so the right run is stopped.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Stop a run in flight (Priority: P1)

An operator opens a running run and clicks **CANCEL RUN**; the engine stops the in-flight step and the run
ends `Cancelled`, which the UI reflects (instantly under the live stream).

**Why this priority**: A run that's stuck, wrong, or no longer wanted should be stoppable from the dashboard
— not left to finish or killed on the server by hand.

**Independent Test**: Open a running run, click **CANCEL RUN**, and see the run become `Cancelled` (and the
in-flight step terminated).

**Acceptance Scenarios**:

1. **Given** a running run, **When** the operator cancels it, **Then** the engine terminates its step and
   the run ends `Cancelled`, and the run-detail view shows `Cancelled`.
2. **Given** the cancel request, **When** the run is actively running, **Then** it is accepted and the run
   flips to `Cancelled` shortly after (no reload needed under the live stream).

---

### User Story 2 - Only running runs are cancellable (Priority: P2)

The **CANCEL RUN** control appears only while a run is actively executing. A finished run has nothing to
cancel, and a run paused at an approval gate is ended with **REJECT** (its own control), not cancel.

**Why this priority**: Offering cancel where it can't act would mislead; a clear, correct affordance matters.

**Independent Test**: The cancel control is absent for terminal and waiting runs; cancelling a non-running
run via the API is rejected with a clear status.

**Acceptance Scenarios**:

1. **Given** a terminal run, **When** the detail view renders, **Then** there is no cancel control, and a
   cancel API call is a conflict.
2. **Given** an unknown run id, **When** cancel is called, **Then** it is not-found.

### Edge Cases

- **Race with completion**: a run that finishes just as cancel is requested is reported "not running"
  (conflict) rather than pretending to cancel.
- **Waiting at a gate**: not cancellable here — the run isn't executing; **REJECT** ends it `Cancelled`.
- **Auth**: cancel is gated by auth like the other write actions.
- **Step termination**: cancelling terminates the step's process tree (the engine's existing guarantee), so
  no orphaned process survives.

## Requirements *(mandatory)*

- **FR-001**: The run-detail view MUST offer a **CANCEL RUN** control while a run is actively executing
  (running/pending), and MUST hide it for terminal and waiting runs.
- **FR-002**: `POST /api/runs/{id}/cancel` MUST cancel the identified in-flight run through the engine so its
  step is terminated and the run ends `Cancelled`.
- **FR-003**: The server MUST address the engine run by the same id it tracks (the run launches with that id
  as its engine run id), so the correct run is cancelled.
- **FR-004**: Cancel MUST report `200` (accepted) for a running run, `409` for a run that is not running
  (terminal or waiting), and `404` for an unknown id.
- **FR-005**: The change MUST be additive (a new endpoint + a new optional engine-run id parameter defaulting
  to today's behavior); no new dependency; cancel is auth-gated like the other write actions.

## Success Criteria *(mandatory)*

- **SC-001**: Cancelling a running run ends it `Cancelled` and terminates its step; the UI shows `Cancelled`.
- **SC-002**: The cancel control shows only for actively-running runs.
- **SC-003**: Cancel returns 200 / 409 / 404 for running / not-running / unknown.
- **SC-004**: No new dependency; existing behavior is unchanged when no run id is supplied to the engine.

## Assumptions

- **The engine already cancels correctly** (terminates the step, ends `Cancelled`, no orphaned process); the
  only missing piece was addressing the run by the server's id. A small optional `runId` on `engine.run`
  (defaulting to a generated id) closes that gap without changing existing callers.
- **Cancel targets actively-running runs.** Waiting-at-a-gate is handled by reject (the run isn't executing),
  and terminal runs are already done — both reported as "not running" rather than silently no-op'd.
