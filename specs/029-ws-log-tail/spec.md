# Feature Specification: WebSocket Log Tail (/ws/runs/{id}/logs)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User: "WebSocket log-tail." The runs-list stream is offered over both SSE (`/api/runs/stream`)
and WebSocket (`/ws/runs`); the per-run log tail was SSE-only (`/api/runs/{id}/logs/stream`, 023). This adds
the **WebSocket variant** `/ws/runs/{id}/logs` — one text frame per recorded line, closing when the run is
terminal — so clients that prefer a socket can tail a run's output the same way.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tail a run's output over a WebSocket (Priority: P1)

A socket client connects to `/ws/runs/{id}/logs` and receives the run's recorded lines as text frames, in
order — the same lines the SSE tail delivers.

**Why this priority**: The run stream already offers both transports; parity means socket clients aren't
forced onto SSE just to follow a run's logs.

**Independent Test**: Connect the socket for a run with recorded output and receive its lines as text
frames.

**Acceptance Scenarios**:

1. **Given** a run with recorded output, **When** a client connects the log socket, **Then** it receives
   each recorded line as a text frame, in order.
2. **Given** a line recorded after the client connects, **When** it lands, **Then** it arrives as a new
   frame without the client re-requesting.

---

### User Story 2 - The socket closes when the run is done (Priority: P1)

When the run reaches a terminal state, the tail drains its final lines and the server closes the socket, so
the client knows the tail is complete — the socket's equivalent of the SSE terminal signal.

**Why this priority**: A tail that never closes leaks sockets; a clean close is what makes it safe to leave
open.

**Independent Test**: Tail a terminal run over the socket; all recorded lines arrive and then the server
closes the connection.

**Acceptance Scenarios**:

1. **Given** a terminal run, **When** the tail catches up, **Then** the server completes the send and closes
   the socket.

### Edge Cases

- **Client disconnects mid-run**: closing the socket cancels the server-side tail (structured concurrency) —
  no leaked polling loop, mirroring the SSE tail.
- **Malformed path** (no run id): the socket is closed with a close frame rather than left hanging.
- **Same source as SSE**: both transports consume the same log-tail Flow, so behavior (ordering, push
  wakeups from 025, terminal completion) is identical — only the wire framing differs.

## Requirements *(mandatory)*

- **FR-001**: The server MUST expose `/ws/runs/{id}/logs` as a WebSocket that emits each recorded log line
  as a text frame, in order — the same lines as the SSE tail (023).
- **FR-002**: The socket MUST deliver lines already recorded at connect time, then each new line as it is
  appended (via the shared log-tail Flow, honoring the 025 poll/push cadence).
- **FR-003**: When the run reaches a terminal state, the server MUST complete the send and close the socket.
- **FR-004**: A client disconnect MUST cancel the server-side tail (cold, structured Flow); a malformed path
  (no id) MUST close the socket with a close frame.
- **FR-005**: The change MUST be additive (a new socket route beside the existing `/ws/runs` and the SSE
  tail); no new dependency; the SSE tail and its web client are unchanged.

## Success Criteria *(mandatory)*

- **SC-001**: Connecting the log socket delivers a run's recorded lines as text frames, in order.
- **SC-002**: A line recorded after connect arrives as a new frame with no re-request.
- **SC-003**: A terminal run's socket receives all lines and then closes.
- **SC-004**: No new dependency; existing suites stay green; SSE and its web client are untouched.

## Assumptions

- **Shared Flow, two transports.** The WebSocket handler consumes the same `RunLogStream` Flow the SSE
  controller uses (mirroring how `/ws/runs` and `/api/runs/stream` share `RunStream`), so the two stay in
  lock-step and there is nothing new to keep in sync.
- **Server-only, like `/ws/runs`.** This is an additive server capability for socket clients; the web UI
  keeps using the SSE tail (023), so there is no web-client change — exactly as the runs-list WebSocket has
  no web client today.
