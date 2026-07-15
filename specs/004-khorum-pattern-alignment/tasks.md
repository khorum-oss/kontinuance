---
description: "Task list for Khorum Pattern Alignment"
---

# Tasks: Khorum Pattern Alignment

**Input**: Design documents from `/specs/004-khorum-pattern-alignment/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Branch**: all work stays on `claude/kontinuance-cross-app-alignment-w3hk0o` (the `004` prefix names
the specs directory only — no feature branch).

**Tests**: This is a build-tooling feature; its "tests" are the quality-gate verifications
(`./gradlew build` green, aggregated coverage includes `engine`, detekt zero violations). US1 carries
a failing-first coverage check per Constitution Principle II.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US6 (Setup/Foundational/Polish carry no story label)
- Paths are repo-root-relative.

---

## Phase 1: Setup

- [X] T001 Verify the baseline build is green before any change: run `./gradlew build` and record that
  root `build.gradle.kts` `koverMergedReport` + `sonar.coverage.jacoco.xmlReportPaths` currently
  reference the `:dsl` module only (the defect US1 fixes).

## Phase 2: Foundational (Blocking Prerequisites)

No blocking foundational work — the six user stories are independently deliverable. US1 (the P1 gate
fix) proceeds first.

**Checkpoint**: baseline recorded; ready for user-story work.

---

## Phase 3: User Story 1 — Quality gates measure the real code (P1) 🎯 MVP

**Goal**: Coverage aggregation + Sonar measure `:engine` (and all production modules), not the empty `:dsl` stub.

**Independent Test**: `./gradlew koverXmlReport` produces `build/reports/kover/report.xml` containing
`engine` packages; an uncovered `engine` path lowers the number.

- [X] T002 [US1] In root `build.gradle.kts`, apply the Kover plugin and declare project aggregation
  `kover(project(":engine"))` and `kover(project(":dsl"))`; delete the bespoke `koverMergedReport`
  task (research.md R3).
- [X] T003 [US1] In root `build.gradle.kts` `sonar { }`, repoint
  `sonar.coverage.jacoco.xmlReportPaths` to the aggregated root report
  `${layout.buildDirectory}/reports/kover/report.xml` (remove the `:dsl`-only path).
- [X] T004 [US1] Verify (quickstart §1): run `./gradlew koverXmlReport`, assert `engine` classes appear
  in `build/reports/kover/report.xml`. Failing-first proof: temporarily add an untested branch in an
  `engine` class, confirm the coverage number drops, then revert.

**Checkpoint**: the coverage gate now sees the engine — the highest-value fix, independently shippable.

---

## Phase 4: User Story 2 — Shared detekt configuration (P2)

**Goal**: One shared `config/detekt/detekt.yml` referenced by every module; detekt stays at zero violations.

**Independent Test**: `config/detekt/detekt.yml` exists, each module references it, `./gradlew detekt` is clean.

- [X] T005 [P] [US2] Create `config/detekt/detekt.yml` (`buildUponDefaultConfig = true`; deviations:
  `style.MaxLineLength.maxLineLength: 140`, `style.ReturnCount.active: false`) — research.md R4.
- [X] T006 [US2] Add `config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))`
  to the `detekt { }` block in `engine/build.gradle.kts` and `dsl/build.gradle.kts` (retain existing
  report-format + `jvmTarget` wiring).
- [X] T007 [US2] Verify: `./gradlew detekt` reports zero violations across all modules. If the shared
  config surfaces a finding, fix the code or record the deviation in the shared file — never baseline
  (Principle III).

**Checkpoint**: lint rules are single-sourced and green.

---

## Phase 5: User Story 3 — dependency.env resolution switch (P2)

**Goal**: `-Pdependency.env` selects internal vs. public repos for deps + plugins; CI uses `public`.

**Independent Test**: `./gradlew help -Pdependency.env=public` and a default `./gradlew help` both resolve.

- [X] T008 [US3] In `settings.gradle.kts` `pluginManagement { repositories { } }`, gate on
  `providers.gradleProperty("dependency.env")`: `public` → `gradlePluginPortal()` + `mavenCentral()`;
  keep the `open-reliquary` CDN available in all selections (research.md R2).
- [X] T009 [US3] In root `build.gradle.kts`, refactor `sharedRepositories()` to gate on
  `dependency.env`: `public` → `mavenCentral()` + `google()` + JetBrains repo + `open-reliquary` CDN;
  default → `proxy.location` with fallback to the same public set (no regression to today's build).
- [X] T010 [US3] In `gradle.properties`, add `dependency.env=stage` and `proxy.location=<url>`.
- [X] T011 [US3] Update CI (`.github/workflows/pr-main.yml`, `merge-main.yml` — or the reusable-workflow
  inputs they pass to `khorum-oss/public-cicd`) to build with `-Pdependency.env=public`.
- [X] T012 [US3] Verify (quickstart §3): `./gradlew help -Pdependency.env=public` and default both
  resolve successfully.

**Checkpoint**: environment-switchable resolution in place; CI on the public path.

---

## Phase 6: User Story 4 — integration-tests module (P3)

**Goal**: A dedicated non-publishable `integration-tests` module whose coverage over `:engine` is aggregated.

**Independent Test**: `./gradlew :integration-tests:test` runs a representative IT green; its coverage appears in the aggregate.

- [X] T013 [US4] Register the module in `settings.gradle.kts`: `includeModules("core-test", "dsl", "engine", "integration-tests")`.
- [X] T014 [US4] Create `integration-tests/build.gradle.kts`: no runnable/publishable artifact; deps
  `project(":engine")`, JUnit platform, `project(":core-test")`; detekt `config.setFrom(...)`; Kover
  applied (research.md R5). (No Spring/Testcontainers yet — the v0 engine has no Spring.)
- [X] T015 [US4] In root `build.gradle.kts`, add `kover(project(":integration-tests"))` to the aggregate.
- [X] T016 [P] [US4] Add one representative test in
  `integration-tests/src/test/kotlin/org/khorum/oss/kontinuance/integration/` that drives
  `PipelineEngine.default()` / the CLI end-to-end over a small descriptor and asserts a terminal status.
- [X] T017 [US4] Verify: `./gradlew :integration-tests:test` green; re-run `koverXmlReport` and confirm
  the module's coverage over `:engine` is in the root aggregate.

**Checkpoint**: IT isolation convention established with a live test.

---

## Phase 7: User Story 5 — Spec Kit convention parity (P3)

**Goal**: Per-feature `checklists/` and a current agent-context pointer.

**Independent Test**: `specs/004-khorum-pattern-alignment/checklists/` exists; `CLAUDE.md` points at the 004 plan.

- [X] T018 [P] [US5] Confirm `specs/004-khorum-pattern-alignment/checklists/requirements.md` exists
  (created by `/speckit-specify`); this establishes the per-feature `checklists/` convention going
  forward (FR-007 targets the active feature — retrofitting 001–003 is out of scope).
- [X] T019 [US5] Confirm the `CLAUDE.md` SPECKIT block points at
  `specs/004-khorum-pattern-alignment/plan.md` (set during the plan phase; FR-008).

**Checkpoint**: spec-kit artifacts match the sibling convention.

---

## Phase 8: User Story 6 — Toolchain & housekeeping (P3)

**Goal**: Remove misleading/unused settings, ignore the Kotlin build dir, and bump Kotlin if compatible.

**Independent Test**: `micronautVersion` gone; `.kotlin/**` ignored; Kotlin either at 2.3.21 (build green) or deferred with a recorded blocker.

- [X] T020 [P] [US6] Remove the unused `micronautVersion=4.5.3` line from `gradle.properties` (FR-010).
- [X] T021 [P] [US6] Add `.kotlin/**` to `.gitignore` (FR-011).
- [X] T022 [US6] Attempt the Kotlin bump: set `kotlin = "2.3.21"` and the matching KSP pin in
  `gradle/libs.versions.toml`, then run `./gradlew build`. If KSP/Konstellation are incompatible
  (research.md R1), revert to `2.1.20` and record the deferral in `research.md` R1 and `docs/roadmap.md`.

**Checkpoint**: build surface clean; toolchain current or deferral documented.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T023 Regenerate `gradle/verification-metadata.xml` for any changed/added dependency
  (`./gradlew --write-verification-metadata sha256,pgp build`); confirm verification stays enabled and
  no secrets are added (Principle V).
- [X] T024 Full acceptance (quickstart §Full): `./gradlew build -Pdependency.env=public` green; run the
  quickstart checks §1–§7; confirm every out-of-scope capability in FR-012 remains present (SC-007).
- [X] T025 Update `docs/roadmap.md` to record 004 (khorum pattern alignment) and the Kotlin-bump outcome.

---

## Dependencies & Execution Order

- **Phase 1 (Setup)** → then user stories.
- **US1 (P1)** first — the MVP; independently shippable.
- **US2, US3 (P2)** independent of each other and of US1.
- **US4 (P3)** depends on US2 (uses the shared detekt config) and US1/US3 wiring (adds itself to the
  aggregate and resolves via `dependency.env`); do US4 after US1–US3.
- **US5 (P3)** independent (docs/spec-kit only).
- **US6 (P3)** mostly independent; the Kotlin bump (T022) runs late so a deferral doesn't block the others.
- **Phase 9 (Polish)** last — verification metadata + full acceptance + roadmap.

## Parallel Opportunities

- T005, T020, T021 are `[P]` (distinct files: `config/detekt/detekt.yml`, `gradle.properties`, `.gitignore`).
- T016 `[P]` (new test file) can be authored while T013–T015 wiring settles.
- US5 (T018–T019) can proceed anytime in parallel with build work.

## Implementation Strategy

- **MVP = US1** (T001–T004): repair the coverage gate. Ship-worthy alone.
- Then P2 (US2 shared detekt, US3 dependency.env), then P3 (US4 module, US5 parity, US6 toolchain),
  then Polish. Verify the build green after each story; never weaken a gate to pass (Principle III).
