# Tasks: WebSocket Log Tail (/ws/runs/{id}/logs)

**Feature**: 029-ws-log-tail | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Server

- [X] T001 `RunLogWebSocketHandler` (`WebSocketHandler`): extract `{id}` from the socket path via a
  `PathPattern`; consume `RunLogStream.updates(id)`, map each line to a text frame, bridge with `asFlux()`
  into `session.send`; a malformed path → close frame. Terminal run → Flow completes → socket closes.
- [X] T002 `WebSocketConfig`: map `RunLogWebSocketHandler.PATH` (`/ws/runs/{id}/logs`) beside `/ws/runs` in
  the `SimpleUrlHandlerMapping`.

## Tests

- [X] T003 `RunLogStreamIT`: add a WebSocket test (`ReactorNettyWebSocketClient`) beside the SSE test — a
  seeded terminal run's lines arrive as text frames and the socket closes on completion.

## Docs

- [X] T004 `docs/getting-started.md`, `docs/roadmap.md`: the log tail is available over SSE **and** WebSocket
  (029), mirroring the runs-list stream.

## Verification

- [X] T005 `:server:test :server:detekt -Pdependency.env=public` green; no new dependency.
