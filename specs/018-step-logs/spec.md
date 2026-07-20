# Feature Specification: Durable Step Logs

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-20

**Status**: Draft

**Input**: User description: "Streamed step logs — the engine records run metadata but no per-step logs, so the run-detail log view is presentational. Capture the step output the engine already produces, store it per run, serve it, and show it in the UI."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See a run's real output (Priority: P1)

An operator opens a run's detail view and sees the actual command output the pipeline produced — the same
secret-masked, step-prefixed lines the engine emits — instead of a placeholder.

**Why this priority**: The run-detail log panel is the primary way to understand *what a run did* and *why
it failed*. Today it shows filler text; without real output the dashboard can't answer the first question an
operator asks. This is the headline gap.

**Independent Test**: Trigger a run whose steps print output; open the run detail and confirm the printed
lines appear, each attributed to its step, with any secret value masked.

**Acceptance Scenarios**:

1. **Given** a completed run that produced output, **When** the operator opens its detail view, **Then** the
   run's log lines are shown in order, each prefixed with the step that produced it.
2. **Given** a step that echoed a configured secret, **When** its log is shown, **Then** the secret value is
   masked (never rendered in clear text).
3. **Given** a run that produced no output, **When** its detail view is opened, **Then** an explicit "no
   output" state is shown (not an error).

---

### User Story 2 - Watch output accumulate while a run is active (Priority: P2)

While a run is still executing (or paused at a gate), the operator watching its detail view sees new output
appear without manually reloading the page.

**Why this priority**: Near-live feedback is what makes the log view useful *during* a run, not just after.
It builds directly on US1 (the same stored lines), so it is a natural second slice rather than a
prerequisite.

**Independent Test**: Open the detail view of an active run; as the run emits more lines, the view updates to
include them within a couple of seconds, and stops updating once the run reaches a terminal state.

**Acceptance Scenarios**:

1. **Given** an active run on its detail view, **When** the run emits additional lines, **Then** the view
   reflects them shortly after without a manual reload.
2. **Given** a run that reaches a terminal state, **When** it finishes, **Then** the view stops polling for
   more output.

### Edge Cases

- **Concurrent runs**: each run's output is stored and served under its own id; lines from one run never
  appear in another's log.
- **A run with no recorded output** (e.g. only approval steps): the log reads as empty, not missing.
- **Unknown run id**: requesting logs for an id with no stored output returns an empty log, consistently with
  "no output" (a genuinely unknown run still 404s on the run itself).
- **Masking parity**: logs are stored already-masked (the same masking the engine applies to streamed
  output), so nothing unmasked is ever persisted or served.
- **Large output**: the stored log grows with the run; the MVP serves the whole recorded log (bounding /
  pagination is a follow-up).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST capture the line-oriented, secret-masked step output the engine already
  produces for a run and persist it durably, attributed to that run.
- **FR-002**: Persisted log lines MUST retain their engine formatting, including the step-name prefix, and
  MUST be stored already-masked (no unmasked secret is ever written).
- **FR-003**: The system MUST expose a run's recorded log for retrieval by run id, returning the lines in the
  order they were produced.
- **FR-004**: A request for a run with no recorded output MUST return an empty log (not an error).
- **FR-005**: Each run's log MUST be isolated: output from concurrent runs MUST NOT intermingle.
- **FR-006**: The run-detail UI MUST display a run's recorded log in place of the current placeholder, and
  MUST show an explicit empty state when there is none.
- **FR-007**: While a run is active, the UI MUST refresh the log without a manual reload, and MUST stop once
  the run reaches a terminal state.
- **FR-008**: Capturing logs MUST NOT change run execution, status, secret masking, or the existing APIs, and
  MUST introduce no new third-party dependency.

### Key Entities *(include if feature involves data)*

- **Run log**: the ordered sequence of already-masked, step-prefixed output lines a run produced, stored and
  retrieved by run id.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a run that printed output, 100% of its emitted (masked) lines are retrievable by run id in
  order, each carrying its step prefix.
- **SC-002**: A configured secret echoed by a step never appears unmasked in the stored or served log.
- **SC-003**: The run-detail view shows the real log for a finished run, and an explicit empty state for a
  run with no output.
- **SC-004**: While a run is active, newly produced lines appear in the detail view without a manual reload;
  polling stops at a terminal state.
- **SC-005**: Two concurrent runs each show only their own output.
- **SC-006**: No new dependency is added and the existing run/stream/trigger APIs are unchanged.

## Assumptions

- **Builds on the existing log seam**: the engine already streams masked, `[step] `-prefixed lines to a
  pluggable sink; this feature records those lines per run rather than changing how they are produced.
- **MVP is recorded logs + live refresh by polling.** A dedicated live **SSE/WebSocket log-tail** channel
  (pushing lines as they occur) and **per-step grouping / pagination / size bounds** are follow-ups; the
  polling refresh gives near-live behavior without a second streaming channel.
- **Durability matches the run store**: logs are file-backed alongside the run store, single-instance, and do
  not need to survive independently of the run record. A resumed run (after an approval gate) appends to the
  same run's log.
- **Retention**: logs live as long as the run store's data; a separate retention/rotation policy is out of
  scope.
