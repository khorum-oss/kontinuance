# Implementation Plan: Run History Persistence

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` (006 numbers the specs dir only) | **Date**: 2026-07-15 | **Spec**: [spec.md](./spec.md)

## Summary

A durable **run history** behind a small `RunStore` seam, plus consolidation of the 003 poll cursor.
New `persistence` module (engine-only, Spring-free); records are JSON on disk via the catalog's
`kotlinx-serialization-json` runtime (`buildJsonObject`/`parseToJsonElement` — no compiler plugin, no
new dependency). The github CI service records every run it executes. A DB backend can replace the
file default behind the interface in the Server/API feature.

## Technical Context

- **New module `persistence`** depending on `:engine` (for `Run`/`PipelineStatus`); registered in
  `settings.gradle.kts` + the root Kover aggregate; shared detekt config.
- **`RunRecord`** — serializable summary: `id`, `pipeline`, `status` (+ failing step), `startedAt`,
  `endedAt`, optional CI context (`repo`, `sha`, `trigger`). Mapped from a `Run` + context via
  `RunRecord.from(...)`. JSON hand-built/parsed (consistent with the github client — no serialization plugin).
- **`RunStore`** interface: `record(RunRecord)`, `recent(limit): List<RunRecord>`, `get(id): RunRecord?`.
  - `FileRunStore` — one JSON file per run under a dir + newest-first listing by file mtime/name; a
    malformed file is skipped, not fatal (FR-007).
  - `InMemoryRunStore` — tests / no-durability default.
  - `NoOpRunStore` — the "no store configured" sink (FR edge case).
- **Cursor consolidation**: move the github `CursorStore` durable placeholder under the same store
  directory (a `CursorStore` impl in `persistence`, or the `FileRunStore` dir houses the cursor file);
  the github service reads/writes its cursor through it. Keep the `CursorStore` interface in `github`
  or lift a shared one — decision in tasks (favor: `persistence` provides a `FileCursorStore` the
  service uses, retiring github's own file placeholder).
- **Wiring**: `EventSource` gains an optional `RunStore` (default `NoOpRunStore`); after a run's
  terminal report, it records a `RunRecord.from(run, event)`. The `kontinuance-ci` runner constructs a
  `FileRunStore` under `~/.kontinuance/`.
- **No secret values persisted** (FR-009): records carry status/context only, never step logs/secrets.

## Constitution Check

- **I** PASS — no engine/DSL/API change; additive module + optional wiring.
- **II** PASS — durable round-trip + restart exercised against a real temp filesystem (the real boundary).
- **III** PASS — new module under detekt/Kover; gates green; nothing weakened.
- **IV** N/A (no codegen). **V** PASS — no new dependency; verification-metadata untouched; no secrets on disk.

## Project Structure

```text
persistence/                                   # NEW module
└── src/{main,test}/kotlin/org/khorum/oss/kontinuance/persistence/
    ├── RunRecord.kt        # serializable summary + RunRecord.from(run, ctx)
    ├── RunStore.kt         # interface + NoOpRunStore
    ├── FileRunStore.kt     # durable JSON-per-run
    ├── InMemoryRunStore.kt
    └── FileCursorStore.kt  # durable cursor consolidated here (github uses it)

github/  engine/                                # engine unchanged; github wires RunStore into EventSource
```

**Structure Decision**: one new `persistence` module; `github` depends on it for recording + the
consolidated cursor. Engine stays untouched and Spring-free.
