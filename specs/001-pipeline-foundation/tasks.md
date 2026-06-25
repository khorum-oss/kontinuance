---

description: "Task list for Pipeline Execution Foundation (v0)"
---

# Tasks: Pipeline Execution Foundation (v0)

**Input**: Design documents from `/specs/001-pipeline-foundation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED — Constitution Principle II (Test-First & Integration-Verified)
makes tests mandatory, not optional. Step execution is verified against the real
`ProcessBuilder` boundary, not mocked.

**Organization**: Tasks are grouped by user story (US1–US3) so each is independently
implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 (or Setup/Foundational/Polish)
- All paths are under the new `engine/` module unless noted.

## Path Conventions

- Main: `engine/src/main/kotlin/org/khorum/oss/kontinuance/engine/...`
- Test: `engine/src/test/kotlin/org/khorum/oss/kontinuance/engine/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Stand up the `engine` module within the existing Gradle build.

- [X] T001 Bump Konstellation in `gradle/libs.versions.toml`:
  `konstellation-meta-dsl = 1.0.15`, `konstellation-dsl = 2.0.14` (2.x renamed the
  module coordinate to `org.khorum.oss.konstellation:konstellation-dsl`). Still TODO
  here: add the chosen YAML lib + expose `kotlinx-coroutines-core` refs.
- [X] T002 Regenerated `gradle/verification-metadata.xml`
  (`./gradlew --write-verification-metadata sha256,pgp build`); `org.khorum.*` is
  group-trusted so no per-artifact entries were needed, one new transitive key added,
  verification stays enabled and the build is green.
- [X] T003 Create the `engine` module: `engine/build.gradle.kts` (Kotlin/JDK 21,
  coroutines, Konstellation, YAML, `core-test` + JUnit/MockK test deps; detekt + Kover
  wired like the other modules) and register it via `includeModules("engine", ...)` in
  `settings.gradle.kts`.
- [X] T004 [P] Create the package skeleton under
  `engine/src/main/kotlin/.../engine/{model,dsl,descriptor,execution,logging,secret}`
  and the mirrored test tree.

**Checkpoint**: `./gradlew :engine:build` succeeds on an empty module (detekt/Kover green).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared pipeline model + status FSM + secret abstraction every story
depends on. ⚠️ No user story work begins until this is complete.

- [X] T005 [US-shared] Write failing tests for the status model in
  `execution/PipelineStatusTest.kt` (exhaustive `when`, `Failed` carries step+reason).
- [X] T006 [US-shared] Implement the sealed `PipelineStatus` in
  `model/PipelineStatus.kt` (Pending, Queued, Running, Success, Failed(step, reason),
  Cancelled, TimedOut, Skipped, WaitingOnApproval) per data-model.md.
- [X] T007 [P] [US-shared] Implement core model types `model/Pipeline.kt`,
  `Stage.kt`, `Step.kt`, `Run.kt` (+ `StageRun`/`StepRun`), `SecretRef.kt` with the
  validation rules from data-model.md (non-empty/unique names, timeout>0,
  concurrency≥1).
- [X] T008 [P] [US-shared] Define `secret/SecretSource.kt` (interface) and
  `secret/EnvSecretSource.kt` (v0 env-var backing) with a failing test asserting
  resolution + that an unresolved required secret fails fast.
- [X] T009 [US-shared] Implement the masking filter `logging/SecretMasker.kt` and a
  failing-first test proving a registered secret never appears unmasked in emitted
  lines (SC-003).
- [X] T009a [US-shared] Introduce the **step-type seam** (FR-016): sealed
  `model/StepDefinition.kt` with `RunStep`, the `execution/StepExecutor.kt` interface
  (`supports`/`execute`) + `StepContext`, and an executor registry the engine selects
  from. Failing-first test: an unsupported `StepDefinition` is rejected with a clear
  error; a registered executor is selected by type.

**Checkpoint**: model + status + secret/masking + step-type seam compile and their
unit tests pass.

---

## Phase 3: User Story 1 — Run a pipeline from a YAML descriptor (P1) 🎯 MVP

**Goal**: Parse a YAML descriptor and execute its stages/steps in order in-process,
returning a correct SUCCESS/FAILED Run.

**Independent Test**: Two-stage descriptor of passing commands ⇒ Success; a non-zero
step ⇒ Failed naming the step and no later steps in that stage run.

### Tests for User Story 1 (write first, must FAIL)

- [X] T010 [P] [US1] Descriptor contract tests in `descriptor/PipelineDescriptorTest.kt`
  per `contracts/pipeline-descriptor.schema.md`: valid parse → model; unknown/missing/
  malformed fields → validation error with location, no execution (FR-003).
- [X] T011 [P] [US1] Engine execution integration test in
  `execution/PipelineEngineRunTest.kt` exercising **real `ProcessBuilder`**: ordered
  execution (FR-004), first-failure stops stage + run Failed(step) (FR-005), empty
  pipeline/stage ⇒ Success (edge cases).

### Implementation for User Story 1

- [X] T012 [US1] Implement `descriptor/PipelineDescriptor.kt` (YAML → pipeline model)
  with validation per the descriptor contract (depends on T007).
- [X] T013 [US1] Implement `execution/RunStepExecutor.kt` (the `StepExecutor` for
  `RunStep` from T009a): launch the command via `ProcessBuilder`, capture
  stdout/stderr, return exit-code → step status.
- [X] T014 [US1] Implement `execution/PipelineEngine.kt` `run(...)`: ordered stage/step
  execution that **dispatches each step through the StepExecutor registry** (T009a),
  first-failure short-circuit, Run status aggregation (depends on T006, T009a, T013).
- [X] T015 [US1] Wire stdout log streaming through `SecretMasker` in the runner
  (FR-010) and add validation/error handling for unlaunchable commands (edge case).

**Checkpoint**: US1 fully functional — the MVP. `./gradlew :engine:test` green.

---

## Phase 4: User Story 2 — Kotlin DSL escape hatch (P2)

**Goal**: Author the same pipeline via the Kotlin DSL (on the latest Konstellation
meta-DSL); it produces the same model and runs through the same engine.

**Independent Test**: A DSL-authored pipeline yields a model equivalent to the YAML
one and executes with identical ordering and final status (SC-002).

### Tests for User Story 2 (write first, must FAIL)

- [X] T016 [P] [US2] DSL contract tests in `dsl/PipelineDslTest.kt` per
  `contracts/dsl-and-engine-api.md`: builders produce the expected model.
- [X] T017 [P] [US2] Equivalence test in `dsl/DslDescriptorEquivalenceTest.kt`:
  identical YAML and DSL definitions → equal models → identical run ordering/status.

### Implementation for User Story 2

- [X] T018 [US2] Implement the DSL in `dsl/PipelineDsl.kt`
  (`pipeline { stage { step { run(...) } } }`) on the Konstellation meta-DSL, emitting
  the shared model (depends on T007).
- [X] T019 [US2] Ensure both front-ends share one model + engine path (no divergent
  execution); add the `PipelineEngine.default()` convenience entry point.

**Checkpoint**: US1 + US2 both work; YAML/DSL equivalence proven.

---

## Phase 5: User Story 3 — Lifecycle, isolation & secret-safe logs (P3)

**Goal**: Observable status transitions, per-step working-dir/env isolation + cleanup,
timeout, concurrency cap, and secret masking in streamed logs.

**Independent Test**: Steps isolated (same relative filename, no interference);
transitions observable; timeout ⇒ TimedOut + cleanup; secret masked in stdout.

### Tests for User Story 3 (write first, must FAIL)

- [X] T020 [P] [US3] Isolation test in `execution/StepIsolationTest.kt`: two steps
  writing the same relative path don't interfere; env not leaked; workdir removed after
  terminal status (FR-007/008, SC-004).
- [X] T021 [P] [US3] Timeout test in `execution/StepTimeoutTest.kt`: a sleeping step
  past its `timeout` is killed within ~1s, marked TimedOut, no orphan processes
  (FR-009, SC-005).
- [X] T022 [P] [US3] Concurrency test in `execution/ConcurrencyTest.kt`: with cap K,
  simultaneously Running steps never exceed K (FR-013, SC-006).
- [X] T023 [P] [US3] Status-stream + masking-in-logs test in
  `execution/StatusStreamTest.kt`: transitions emitted in order (FR-006); a secret
  echoed by a step is masked in stdout (FR-011, SC-003).

### Implementation for User Story 3

- [X] T024 [US3] Add isolated temp working-dir creation + scoped environment + process-
  tree cleanup to `StepRunner` (FR-007/008).
- [X] T025 [US3] Add per-step `withTimeout` + `destroyForcibly()`/`descendants()`
  termination → TimedOut (FR-009).
- [X] T026 [US3] Add a `Semaphore(K)` gate in the engine for step launches (FR-013);
  honor `Skipped` for unmet conditions (FR-015).
- [X] T027 [US3] Emit `StatusEvent` transitions via a `Flow`/`SharedFlow` and implement
  `cancel(runId)` → Cancelled with in-flight cleanup (FR-006, FR-014).

**Checkpoint**: all three stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T028 [P] Add `engine` README + ensure `docs/overview.md` references stay accurate.
- [X] T029 Run `quickstart.md` end-to-end against the built module and fix any drift.
- [X] T030 Verify gates: `./gradlew build` (detekt zero violations, Kover verification
  passes — exemptions only via `@ExcludeFromCoverage` with justification), no Sonar
  regression (Constitution Principle III).
- [X] T031 [P] Confirm no template placeholder tokens remain and naming satisfies
  detekt; tidy public API KDoc on the pipeline contract (Principle I).

---

## Dependencies & Execution Order

- **Setup (P1: T001–T004)** → no deps; start immediately.
- **Foundational (P2: T005–T009)** → depends on Setup; **BLOCKS all user stories**.
- **US1 (T010–T015)** → depends on Foundational; the MVP.
- **US2 (T016–T019)** → depends on Foundational; reuses US1's engine path.
- **US3 (T020–T027)** → depends on Foundational; hardens US1/US2's runner + engine.
- **Polish (T028–T031)** → after the desired stories.

### Within each story

Tests written first and FAIL before implementation (Principle II) → models before
services → runner before engine → commit after each task or logical group.

### Parallel opportunities

- T004 alongside T001–T003 finishing; T007/T008 in parallel; all `[P]` test tasks per
  story in parallel; US1/US2/US3 can be staffed in parallel once Foundational is done.

---

## Implementation Strategy

**MVP first**: Setup → Foundational → US1, then STOP and validate US1 independently
(this is the demonstrable v0 core). Then layer US2 (DSL) and US3 (hardening)
incrementally, each independently testable, finishing with Polish/gate verification.
