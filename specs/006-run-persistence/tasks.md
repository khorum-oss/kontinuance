---
description: "Task list for Run History Persistence"
---

# Tasks: Run History Persistence

**Input**: `/specs/006-run-persistence/` (spec.md, plan.md). Branch: `claude/kontinuance-cross-app-alignment-w3hk0o`.
**Tests**: durable round-trip / restart exercised against a real temp filesystem (Constitution II).

## Phase 1: Setup
- [X] T001 Create the `persistence` module: `persistence/build.gradle.kts` (depends `:engine`, serialization-json;
  detekt shared config; Kover); register in `settings.gradle.kts` + add `kover(project(":persistence"))` to root.

## Phase 2: US1 — record runs durably (P1) 🎯 MVP
- [X] T002 [US1] `RunRecord` (serializable summary: id, pipeline, status+failing step, startedAt/endedAt,
  optional repo/sha/trigger) + `RunRecord.from(run, ctx)` mapping from an engine `Run`.
- [X] T003 [US1] `RunStore` interface (`record`/`recent`/`get`) + `NoOpRunStore` + `InMemoryRunStore`.
- [X] T004 [US1] `FileRunStore` — JSON per run (hand-built via `buildJsonObject`, parsed via
  `parseToJsonElement`; no serialization plugin), newest-first listing, malformed file skipped (FR-007).
- [X] T005 [US1] Tests: record→read round-trip, **restart durability** (fresh store over same dir),
  failure status recorded, corrupt-file isolation.

## Phase 3: US2 — list & fetch (P2)
- [X] T006 [US2] Implement/verify `recent(limit)` newest-first + bounded, and `get(id)`; tests for ordering, limit, unknown id.

## Phase 4: US3 — wire in + consolidate cursor (P3)
- [X] T007 [US3] `EventSource` gains an optional `RunStore` (default `NoOpRunStore`); record a `RunRecord.from(run, event)`
  after each terminal report. Test: a CI run produces a readable record with repo/sha/trigger.
- [X] T008 [US3] Consolidate the durable poll cursor into `persistence` (`FileCursorStore` under the store dir); the
  `kontinuance-ci` runner uses the persistence store dir under `~/.kontinuance/`. Keep behavior (resume across restart).

## Phase 5: Polish
- [X] T009 Full `./gradlew build` green (new module under gates; coverage aggregated); update `docs/roadmap.md` (006 built).
