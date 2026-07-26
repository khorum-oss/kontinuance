# Implementation Plan: WebSocket Log Tail (/ws/runs/{id}/logs)

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/029-ws-log-tail/spec.md`

## Summary

Add a `RunLogWebSocketHandler` that tails one run's output over a WebSocket, mirroring the existing
`RunWebSocketHandler` (`/ws/runs`). It extracts the run id from the socket path (`/ws/runs/{id}/logs`) with a
`PathPattern`, consumes the same `RunLogStream` Flow the SSE tail (023) uses, and bridges it to the reactive
`send` pipeline with `asFlux()` — one text frame per line. When the run is terminal the Flow completes and
the server closes the socket; a client disconnect cancels the collector (structured concurrency). Registered
in `WebSocketConfig` beside `/ws/runs`. Server-only (the web UI keeps using the SSE tail); additive, no new
dependency.

## Technical Context

**Language/Version**: Kotlin/JDK 21 + Spring Boot WebFlux (`:server`), kotlinx-coroutines-reactor.

**Primary Dependencies**: none new — reuses `RunLogStream`, `asFlux`, WebFlux WebSocket support.

**Storage**: none new.

**Testing**: `RunLogStreamIT` gains a WebSocket test (`ReactorNettyWebSocketClient`) alongside its SSE test —
a seeded terminal run's lines arrive as text frames and the socket closes on completion (mirrors
`RunStreamIT`'s SSE+WS pair).

**Constraints**: shared Flow with the SSE tail (lock-step behavior); cold/structured (disconnect cancels the
poll); malformed path → close frame; no new dependency; SSE + web client unchanged.

**Scale/Scope**: `server/.../logs/RunLogWebSocketHandler.kt` (new) + `WebSocketConfig.kt` (map the path);
`RunLogStreamIT.kt` (WS test); docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive socket route beside `/ws/runs` and the SSE
  tail; no existing contract changes.
- **II. Test-First & Integration-Verified**: PASS — the WS tail is integration-tested over a real socket
  against a seeded terminal run.
- **III. Quality Gates**: PASS — detekt/Kover on `:server`.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency.

No violations → Complexity Tracking empty.

## Project Structure

```text
server/.../logs/RunLogWebSocketHandler.kt      # NEW — /ws/runs/{id}/logs; text frame per line; closes on terminal
server/.../WebSocketConfig.kt                   # EDIT — map RunLogWebSocketHandler.PATH beside /ws/runs
server/.../logs/RunLogStreamIT.kt (test)        # EDIT — a WebSocket test beside the SSE test
docs/getting-started.md, docs/roadmap.md         # EDIT — the log tail is available over SSE and WebSocket (029)
```

**Structure Decision**: Mirror `RunWebSocketHandler` one-to-one for the per-run log tail — same `asFlux()`
bridge, same shared-Flow source — so the two transports (SSE + WS) stay in lock-step with nothing extra to
keep in sync. Keep it server-only, exactly as the runs-list WebSocket is today.

## Complexity Tracking

> No Constitution Check violations — no entries.
