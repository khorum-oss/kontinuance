---
description: "Task list for Server / Read API"
---

# Tasks: Server / Read API

**Input**: `/specs/007-server-api/` (spec.md, plan.md, contracts/api-contract.md). Branch: `claude/kontinuance-cross-app-alignment-w3hk0o`.
**Tests**: handlers unit-tested + a real `HttpClient` round-trip against the running server (Constitution II).

## Phase 1: Setup
- [ ] T001 Create the `server` module: `server/build.gradle.kts` (depends `:persistence`, serialization-json;
  detekt shared config; Kover; `application` mainClass → `...server.cli.ApiServerMainKt`, install task);
  register in `settings.gradle.kts` + add `kover(project(":server"))` to root.

## Phase 2: Foundational
- [ ] T002 `ApiResponse` (`status: Int`, `json: String`) — the transport-agnostic handler result (FR-008).
- [ ] T003 `JsonView` — `RunRecord` → JSON object (reuse `RunRecord.toJson`) and a `runs` array via `buildJsonArray` (FR-005).

## Phase 3: US1 — list recent runs (P1) 🎯 MVP
- [ ] T004 [US1] `RunApi.listRuns(limit: Int?)` → `ApiResponse(200, {"runs":[…]})` newest-first from `RunStore.recent`,
  limit lenient: absent/invalid → default 50, clamp to max 500 (FR-001/FR-002).
- [ ] T005 [US1] Tests: list newest-first + limit bounding (default, over-cap, invalid), empty store → `{"runs":[]}`.

## Phase 4: US2 — get run by id (P1)
- [ ] T006 [US2] `RunApi.getRun(id)` → `ApiResponse(200, <run>)` or `ApiResponse(404, {"error":"not found"})` (FR-003).
- [ ] T007 [US2] `RunApi.health()` → `200 {"status":"ok"}` (FR-004). Tests for getRun (known/unknown) + health.

## Phase 5: US3 — runnable service over the real store (P2)
- [ ] T008 [US3] `HttpApiServer` — bind `RunApi` onto the JDK `HttpServer`: routes `/api/health`, `/api/runs`,
  `/api/runs/{id}`; unknown path → 404, bad method → 405 (FR-006); `start(host,port)`/`stop()`; parse `?limit`.
- [ ] T009 [US3] `cli/ApiServerMain` launcher (`kontinuance-api`): `FileRunStore(~/.kontinuance/runs)`, host/port
  from args/env (default 127.0.0.1:8077), `--store` override, clean fail on bind error (FR-007).
- [ ] T010 [US3] Tests: **real `HttpClient` round-trip** against a started `HttpApiServer` over a temp store —
  health, runs list, run-by-id, 404 unknown id, 404 unknown route; confirm served runs match the store (SC-004/SC-007).

## Phase 6: Polish
- [ ] T011 Full `./gradlew build` green (new module under gates; coverage aggregated); update `docs/roadmap.md` (007 built);
  add an `examples/` note / quickstart pointer for `kontinuance-api`.

## Dependencies
- Setup (T001) → Foundational (T002–T003) → US1 (T004–T005) → US2 (T006–T007) → US3 (T008–T010) → Polish.
- US1+US2 (handlers) are pure logic (no server); US3 wires the transport + launcher over them. MVP = US1 (+ foundational).

## Parallel opportunities
- T002, T003 `[P]` (distinct files). T005/T007 tests can be authored alongside their handlers.
