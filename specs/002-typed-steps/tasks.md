---

description: "Task list for First-Class Gradle / Docker / NPM Step Types"
---

# Tasks: First-Class Gradle / Docker / NPM Step Types

**Input**: Design documents from `/specs/002-typed-steps/`

**Prerequisites**: feature 001 implemented (StepDefinition + StepExecutor seam,
StepContext); plan.md, spec.md, research.md, data-model.md, contracts/.

**Tests**: INCLUDED — Constitution Principle II. Executor tests assert CLI command
construction (pure) and exercise the real tool where available (environment-gated).

**Organization**: grouped by user story (US1 Gradle / US2 Docker / US3 NPM), each an
independent slice on the shared seam.

## Path Conventions

- Main: `engine/src/main/kotlin/org/khorum/oss/kontinuance/engine/{model,execution,dsl,descriptor}/steps/`
- Test: mirrored under `engine/src/test/kotlin/...`

---

## Phase 1: Setup

- [X] T001 Confirm feature 001's seam is present: `StepDefinition` (sealed),
  `StepExecutor` (`supports`/`execute`), `StepContext`, and the engine executor
  registry. If 001 isn't merged yet, this feature is blocked on it.
- [X] T002 [P] Create the `steps/` package skeletons under `model/`, `execution/`,
  `dsl/`, `descriptor/` in the `engine` module (+ mirrored test packages).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T003 Add a shared `descriptor` rule: a step declares exactly one of
  `run`/`gradle`/`docker`/`npm`; write a failing-first test for the "zero or >1 ⇒
  validation error" case (contracts/typed-steps-descriptor.schema.md).
- [X] T004 [P] Establish the Konstellation builder scaffolding for typed steps under
  `dsl/steps/` following `dsl/src/main/kotlin/.../common/{KontinuanceDsl,DslBuilder}.kt`
  and the KSP config in `dsl/build.gradle.kts` (FR-008) — verify generation compiles.

**Checkpoint**: descriptor dispatch + builder scaffolding ready for all three types.

---

## Phase 3: User Story 1 — Gradle steps (P1) 🎯 MVP

**Goal**: First-class `gradleStep` in YAML + DSL, executed via `gradle`/`./gradlew`.

### Tests (write first, must FAIL)

- [X] T005 [P] [US1] `GradleStep` model + validation test (`tasks` non-empty) in
  `model/steps/GradleStepTest.kt`.
- [X] T006 [P] [US1] `GradleStepExecutor` argv-construction test in
  `execution/steps/GradleStepExecutorTest.kt`: `["build"]` + `["-x","test"]` →
  `./gradlew build -x test` (wrapper preferred); missing tool ⇒ FAILED naming it.
- [X] T007 [P] [US1] DSL + descriptor equivalence test
  (`dsl/steps/GradleStepDslTest.kt`): YAML `gradle:` and `gradleStep { }` → equal model.

### Implementation

- [X] T008 [US1] Implement `model/steps/GradleStep.kt` (StepDefinition subtype).
- [X] T009 [US1] Implement `execution/steps/GradleStepExecutor.kt` (`StepExecutor`):
  build argv, run via `StepContext`, map exit code; register into the 001 registry.
- [X] T010 [US1] Implement the `gradleStep` Konstellation builder (`dsl/steps/`) and the
  descriptor `gradle:` mapping (`descriptor/steps/`).

**Checkpoint**: Gradle steps work end-to-end — the MVP of this feature.

---

## Phase 4: User Story 2 — Docker steps (P2)

### Tests (write first, must FAIL)

- [X] T011 [P] [US2] `DockerStep` model test (RUN requires image+command; BUILD
  requires context) in `model/steps/DockerStepTest.kt`.
- [X] T012 [P] [US2] `DockerStepExecutor` argv test in
  `execution/steps/DockerStepExecutorTest.kt`: RUN → `docker run <image> <command>`;
  BUILD → `docker build …`; missing `docker` ⇒ FAILED. Real-`docker` path gated.
- [X] T013 [P] [US2] DSL + descriptor equivalence test for docker.

### Implementation

- [X] T014 [US2] Implement `model/steps/DockerStep.kt` (+ `DockerMode`).
- [X] T015 [US2] Implement `execution/steps/DockerStepExecutor.kt`; register it.
- [X] T016 [US2] Implement the `dockerStep` builder + descriptor `docker:` mapping.

**Checkpoint**: Gradle + Docker steps both work.

---

## Phase 5: User Story 3 — NPM steps (P3)

### Tests (write first, must FAIL)

- [X] T017 [P] [US3] `NpmStep` model test (SCRIPT requires script) in
  `model/steps/NpmStepTest.kt`.
- [X] T018 [P] [US3] `NpmStepExecutor` argv test: SCRIPT → `npm run <script>`;
  INSTALL → `npm ci`/`npm install`; missing `npm` ⇒ FAILED. Real-`npm` path gated.
- [X] T019 [P] [US3] DSL + descriptor equivalence test for npm.

### Implementation

- [X] T020 [US3] Implement `model/steps/NpmStep.kt` (+ `NpmMode`).
- [X] T021 [US3] Implement `execution/steps/NpmStepExecutor.kt`; register it.
- [X] T022 [US3] Implement the `npmStep` builder + descriptor `npm:` mapping.

**Checkpoint**: all three typed step types work independently.

---

## Phase 6: Polish & Cross-Cutting

- [X] T023 [P] Cross-type test: a mixed-step pipeline (run + gradle + docker + npm)
  executes correctly; a typed step and an equivalent `run` step show identical
  isolation + masking (SC-005).
- [X] T024 Update `docs/diagrams/class-diagram.md` to show the three new subtypes +
  executors plugged into the seam.
- [X] T025 Verify gates: `./gradlew build` (detekt zero violations, Kover passes —
  exemptions only via `@ExcludeFromCoverage` w/ justification); confirm **no**
  feature-001 engine-loop code changed (SC-003).
- [X] T026 Run `quickstart.md` end-to-end and fix any drift.

---

## Dependencies & Execution Order

- **Setup (T001–T002)** → T001 confirms the 001 seam (hard prerequisite).
- **Foundational (T003–T004)** → descriptor dispatch + builder scaffolding; blocks the
  stories.
- **US1 Gradle (T005–T010)** → the MVP; independent slice.
- **US2 Docker (T011–T016)** / **US3 NPM (T017–T022)** → independent slices on the seam;
  can be staffed in parallel after Foundational.
- **Polish (T023–T026)** → after the desired stories.

### Within each story

Tests first and FAIL before implementation (Principle II) → model → executor (+register)
→ builders/descriptor mapping → commit per task or logical group.

---

## Implementation Strategy

Deliver Gradle (US1) first as the feature MVP, validate independently, then add Docker
and NPM as independent increments. Each new type is purely additive on the 001 seam —
no engine-loop changes (SC-003).
