# Tasks: Source Checkout & Shared Workspace

**Feature**: 015-source-checkout | **Branch**: `claude/source-checkout`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | Contract: [checkout-step.md](./contracts/checkout-step.md)

Engine-only — usable via the descriptor the server already runs. **MVP** = US1 (shared workspace).

---

## Phase 1: Foundational

- [x] T001 Re-confirm the touch-points against source: `StepRunner` (per-step temp dir), `DefaultPipelineEngine.run/executeStages`, `PipelineDescriptor` DEFINITION_KEYS, the DSL `*StepBuilder` pattern, `StepIsolationTest`

## Phase 2: User Story 1 — Shared per-run workspace (Priority: P1) 🎯 MVP

- [x] T002 [US1] Edit `DefaultPipelineEngine.run` to create one workspace (`Files.createTempDirectory("knt-run-")`) per run and remove it in the run's `finally` (terminal, paused, or cancelled); pass it into the `StepRunner`
- [x] T003 [US1] Edit `StepRunner` to run each step in the shared workspace (resolve `workingDir` inside it; no per-step create/delete); keep secret resolution + env scoping per step
- [x] T004 [US1] Update `StepIsolationTest`: invert "steps do not interfere" → **steps share the workspace** (writer's file is visible to a later reader); keep the env-scoping test; change the cleanup test to assert no leftover `knt-run-` workspace
- [x] T005 [P] [US1] Add `WorkspaceSharingTest`: a two-step run writes then reads a relative file (Success), and a `workingDir` sub-path stays inside the workspace

**Checkpoint**: multi-step runs share a workspace; nothing is left behind.

## Phase 3: User Story 2 — `git:` checkout in the descriptor (Priority: P1)

- [x] T006 [P] [US2] Add `model/GitStep.kt` (`url` required, `ref?`, `dir="."`, `depth=1`)
- [x] T007 [P] [US2] Add `execution/steps/GitStepExecutor.kt` (extends `ProcessStepExecutor`; argv `git clone [--depth d] [--branch ref] url dir`)
- [x] T008 [US2] Register `GitStepExecutor` in `PipelineEngine.default()`
- [x] T009 [US2] Edit `PipelineDescriptor`: add `git` to `DEFINITION_KEYS` + `GIT_KEYS` + `parseGit` (url required; ref/dir/depth optional)
- [x] T010 [P] [US2] Extend `TypedStepDescriptorTest`: `git:` maps to `GitStep` with its fields
- [x] T011 [P] [US2] Add `steps/GitStepExecutorTest`: the checkout argv is correct; a real `git clone` of a local temp repo behind `assumeTrue(git on PATH)` populates the workspace

## Phase 4: User Story 3 — `gitStep` in the DSL (Priority: P1)

- [x] T012 [P] [US3] Add `dsl/steps/GitStepDsl.kt` — a `GitStepBuilder` + `gitStep(name) { … }` extension on `StepDslBuilder.Group`, using `configureStep`
- [x] T013 [P] [US3] Add `GitStepDslTest`: `gitStep { … }` builds the same `GitStep` as the equivalent `git:` YAML

## Phase 5: Polish

- [x] T014 Run `:engine:test` + `:engine:detekt`; confirm workspace sharing, cleanup, `git:` parse, DSL equivalence, and checkout argv all pass and gates are green (SC-001..005)
- [x] T015 [P] Confirm no new dependency (verification metadata untouched) and no external design link

---

## Dependencies

- T001 → all. US1 (T002–T005) is the workspace foundation; the checkout (US2/US3) is only useful on top of
  it but its *model/parse/DSL* (T006, T009, T010, T012, T013) can be written in parallel with US1.
- T008 (register) needs T007. T014/T015 last.

## Parallel Opportunities

- `[P]`: T005 (test), T006 (model), T007 (executor), T010/T011 (tests), T012 (DSL), T013 (test), T015
  (review). Edits to shared files — `PipelineEngine.default` (T008), `PipelineDescriptor` (T009),
  `StepRunner`/engine (T002/T003), `StepIsolationTest` (T004) — are sequential per file.

## Implementation Strategy

- **MVP**: Phase 1–2 (shared workspace) → multi-step builds possible. **Increment**: the `git:` step
  (US2) and its DSL twin (US3), then Polish. **Verification**: `:engine:test` + `:engine:detekt`.
