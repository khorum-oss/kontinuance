# Feature Specification: Live Log Tail (SSE)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User: "Lets do the log tail one." The 018 follow-up: step logs are recorded and the run-detail view
re-fetches them on a 1.5s interval while a run is active. This replaces that client-side polling with a
real **server-streamed live tail** — a Server-Sent Events endpoint that pushes each recorded line as it
lands and closes cleanly once the run is terminal — so the operator watches output arrive live instead of
in polled batches.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Watch a run's output stream live (Priority: P1)

An operator opens a running pipeline's detail view and sees its log lines appear as they are produced,
without a visible refresh, until the run finishes.

**Why this priority**: 018 made logs *available*; a live tail makes them *feel live*. Polling shows output
in 1.5s stutters and keeps re-fetching the whole log; a stream delivers each line as it lands.

**Independent Test**: Open a run that already has recorded lines; the detail view shows those lines from the
stream (not a one-shot fetch), and any later line appears without a reload.

**Acceptance Scenarios**:

1. **Given** a run with recorded output, **When** its detail view opens, **Then** the recorded lines are
   delivered over the live stream and shown in order.
2. **Given** a run still producing output, **When** a new line is recorded, **Then** it appears in the view
   without a page reload or manual refresh.

---

### User Story 2 - The stream ends when the run is done (Priority: P1)

Once a run reaches a terminal state (success/failed/cancelled/timed-out/skipped), the stream drains its
final lines and closes, so the client stops tailing and settles the run's final status — no endless open
connection, no reconnect storm.

**Why this priority**: A live tail that never ends leaks connections and hammers reconnects. A clean
terminal signal is what makes streaming safe to leave open.

**Independent Test**: Tail a terminal run; the stream delivers all recorded lines and then signals
completion, after which the client closes the connection and does not reconnect.

**Acceptance Scenarios**:

1. **Given** a terminal run, **When** the tail catches up to the last recorded line, **Then** the server
   emits a terminal end signal and completes the stream.
2. **Given** that end signal, **When** the client receives it, **Then** it closes the connection (no
   reconnect) and re-settles the run's final status.

### Edge Cases

- **Client disconnects mid-run**: closing the connection cancels the server-side tail (structured
  concurrency) — no leaked polling loop.
- **Unknown / not-yet-recorded run**: the tail simply yields no lines and stays open until the run appears
  terminal or the client leaves; it is never an error (mirrors the 018 one-shot, which 200s an empty log).
- **No streaming transport (SSR / no `EventSource`)**: the tail is inert (no lines) rather than throwing;
  the one-shot `GET /api/runs/{id}/logs` (018) remains as the non-streaming fetch.

## Requirements *(mandatory)*

- **FR-001**: The server MUST expose `GET /api/runs/{id}/logs/stream` as a `text/event-stream` that emits
  each recorded log line, in order, as a `log` event — the already-masked, step-prefixed lines from the 018
  log store.
- **FR-002**: The stream MUST deliver every line already recorded at subscription time, then each new line
  as it is appended, without the client re-fetching the whole log.
- **FR-003**: When the run reaches a terminal state, the stream MUST drain its remaining lines and then emit
  a terminal `end` event and complete, so the client stops tailing.
- **FR-004**: The stream MUST be driven by a cold, structured source so a disconnecting client cancels the
  server-side work (no leaked loops), consistent with the existing run stream (`/api/runs/stream`).
- **FR-005**: The run-detail view MUST consume this stream in place of its interval polling, and MUST re-fetch
  the run's summary on the terminal signal so the final status/timing settle. The one-shot
  `GET /api/runs/{id}/logs` (018) MUST remain available; no new dependency.

## Success Criteria *(mandatory)*

- **SC-001**: Opening a run's detail view delivers its recorded lines over the live stream, in order.
- **SC-002**: A line recorded after the view is open appears with no reload or manual refresh.
- **SC-003**: A terminal run's stream ends with a terminal signal; the client closes and does not reconnect.
- **SC-004**: No interval polling remains in the run-detail view; the one-shot log endpoint still works.
- **SC-005**: No new dependency; existing server and web suites stay green.

## Assumptions

- **Same polling-over-shared-store model as `/api/runs/stream`.** The `:server` process is separate from the
  writer, so the live source of truth is the on-disk log store; the tail polls it on a fast interval behind a
  cold `Flow`. A push backend can later replace the poll behind the same stream surface without touching the
  controller or the client. (023 keeps SSE; a WebSocket variant is out of scope.)
- **Terminal-state vocabulary** matches the run status strings (Success/Failed/Cancelled/TimedOut/Skipped);
  anything else (Running/Waiting/Pending) keeps the tail open.
