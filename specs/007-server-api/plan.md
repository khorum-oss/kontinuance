# Implementation Plan: Server / Read API

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` (007 numbers the specs dir only) | **Date**: 2026-07-15 | **Spec**: [spec.md](./spec.md)

## Summary

Stand up a small long-running HTTP service exposing the 006 run history over a stable read API
(`/api/health`, `/api/runs`, `/api/runs/{id}`) so the Web UI and operators can list and inspect runs.
First increment is **Spring-free** on the JDK `com.sun.net.httpserver.HttpServer` with **zero new
dependencies** (JSON via the catalog's `kotlinx-serialization-json`). Read logic lives in
transport-agnostic handlers so a Spring Boot / SSE transport can wrap the same behavior later.

## Technical Context

**Language/Version**: Kotlin 2.3.21 / JDK 21 (repo current).

**Primary Dependencies**: `:persistence` (`RunStore`, `RunRecord`) for the data; JDK `HttpServer` for
the transport; the catalog's `kotlinx-serialization-json` runtime for responses (`buildJsonArray` /
`RunRecord.toJson`). **No new external dependency** (FR-009). No `:engine` dependency needed — the
server reads `RunRecord`s from the store, it does not run pipelines.

**Storage**: none of its own — reads the 006 `RunStore` (file-backed by default).

**Testing**: JUnit 5; handlers tested directly (transport-agnostic, SC-007) and the server tested via a
**real HTTP round-trip** with JDK `java.net.http.HttpClient` (the real boundary, Constitution II) — no
new mock-server dependency.

**Target Platform**: JVM service on a private network (the Hestia Mini).

**Project Type**: Multi-module JVM platform — adds one module, `server`.

**Performance Goals**: homelab scale; correctness + bounded responses over throughput.

**Constraints**: read-only; no auth this increment (private network); responses bounded by a max cap;
the `/api` route/shape is a stable consumer contract (FR-010); Spring-free, no new dependency (FR-009).

**Scale/Scope**: a handful of repos, modest run history; single instance.

## Constitution Check

*GATE: pass before Phase 0; re-check after Phase 1.*

- **I. Platform-First & Stable Public Contract** — PASS. The `/api` routes + JSON shape are a new
  consumer-facing contract (the UI depends on them); kept under a stable prefix and covered by a test
  that pins the shape (FR-010). No existing contract broken.
- **II. Test-First & Integration-Verified** — PASS. The HTTP boundary is exercised end-to-end via a
  real `HttpClient` round-trip against the running server; handlers are also unit-tested.
- **III. Quality Gates Non-Negotiable** — PASS. New module under detekt/Kover; gates green.
- **IV. Correct, Covered Code Generation** — N/A (no KSP).
- **V. Supply-Chain Integrity** — PASS. No new dependency; `verification-metadata.xml` untouched; the
  API exposes no secrets (records carry none). Spring Boot adoption — which *would* require verification
  work — is explicitly deferred to a separate change (documented in research.md).

**Result: PASS** — Complexity Tracking empty.

## Project Structure

```text
server/                                         # NEW module (settings + Kover aggregate)
└── src/{main,test}/kotlin/org/khorum/oss/kontinuance/server/
    ├── RunApi.kt          # transport-agnostic handlers: health(), listRuns(limit), getRun(id) -> ApiResponse
    ├── ApiResponse.kt     # {status:Int, json:String} — the handler result, independent of HTTP server
    ├── JsonView.kt        # RunRecord -> JSON object / array (reuses RunRecord.toJson; buildJsonArray)
    ├── HttpApiServer.kt   # binds RunApi onto the JDK HttpServer (/api routes); start/stop; host/port
    └── cli/ApiServerMain.kt  # `kontinuance-api` launcher: FileRunStore(~/.kontinuance/runs), host/port

persistence/  engine/                            # unchanged; server depends on :persistence only
```

**Structure Decision**: one new `server` module depending on `:persistence`. `RunApi` (handlers) is
separated from `HttpApiServer` (transport) so the same read logic can later back a Spring/SSE layer
without change (FR-008). Engine untouched and Spring-free.

## Complexity Tracking

No violations — section intentionally empty.
