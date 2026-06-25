# Feature Specification: First-Class Gradle / Docker / NPM Step Types

**Feature Branch**: `002-typed-steps`

**Created**: 2026-06-24

**Status**: Draft

**Input**: User description: "Make gradle, docker, and npm first-class in the pipeline DSL — configured, typed step builders (gradleStep/dockerStep/npmStep) instead of hand-written shell strings — built on the v0 step-type seam (StepDefinition + StepExecutor)."

## Context

Builds directly on feature `001-pipeline-foundation`, which established the engine,
the hybrid (YAML + Kotlin) pipeline definition, and the **step-type extensibility
seam** (FR-016: sealed `StepDefinition` + `StepExecutor` registry). This feature adds
three first-class step types so common build tools are configured, type-safe, and
IDE-discoverable rather than expressed as raw `run("…")` shell strings. It is a
follow-up to the v0 MVP and does **not** change the engine's execution loop — it
registers new `StepDefinition` subtypes and their executors.

Scope note: still **in-process** (no container isolation). `dockerStep` *invokes the
`docker` CLI* as a tool; it does not use Docker to isolate the runner (that remains a
v1 concern).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Gradle steps as first-class (Priority: P1)

A developer declares a Gradle build/test step by naming tasks and args rather than
writing a shell line, in either YAML or the Kotlin DSL.

**Why this priority**: Kontinuance itself is a Gradle project and the primary early
audience is Kotlin/Gradle teams; Gradle is the highest-value first typed step and the
MVP of this feature.

**Independent Test**: Define a `gradleStep` running `build` with `-x test`; assert it
produces a `GradleStep` model and executes the equivalent `gradle build -x test`
(via wrapper when present), reporting SUCCESS/FAILED from the Gradle exit code.

**Acceptance Scenarios**:

1. **Given** a `gradleStep` naming tasks `["build"]` and args `["-x","test"]`, **When**
   the step runs, **Then** it invokes Gradle with those tasks/args and maps exit `0`
   to SUCCESS, non-zero to FAILED.
2. **Given** a repository containing `./gradlew`, **When** a `gradleStep` runs, **Then**
   the wrapper is used in preference to a system `gradle` (configurable).
3. **Given** identical Gradle steps written in YAML and in the Kotlin DSL, **When**
   each is built, **Then** they produce the same `GradleStep` model.

---

### User Story 2 - Docker steps as first-class (Priority: P2)

A developer declares a Docker action (build an image, or run a command in an image)
through a typed `dockerStep` that shells out to the `docker` CLI.

**Why this priority**: Containerized build/test steps are common, but secondary to
Gradle for the initial audience; depends on the same seam as US1.

**Independent Test**: Define a `dockerStep` that runs `node:20 -- npm ci`; assert it
produces a `DockerStep` model and invokes `docker run … node:20 …`, mapping the exit
code to status. (CI executes against a real `docker` when available; otherwise the
command-construction is asserted and execution is environment-gated.)

**Acceptance Scenarios**:

1. **Given** a `dockerStep` with `image` and a `run` command, **When** it executes,
   **Then** it invokes `docker run` with that image and command and maps the exit code.
2. **Given** a `dockerStep` configured to build (`dockerfile`/`context`/`tag`), **When**
   it executes, **Then** it invokes `docker build` with those args.
3. **Given** secrets referenced by the step, **When** the docker command runs, **Then**
   secret values are masked in the streamed logs (inherited from the v0 seam).

---

### User Story 3 - NPM steps as first-class (Priority: P3)

A developer declares an npm script/install step through a typed `npmStep`.

**Why this priority**: Rounds out the common front-end/tooling trio; lowest of the
three for the initial Kotlin audience but completes the "first-class build tools" goal.

**Independent Test**: Define an `npmStep` running script `test`; assert it produces an
`NpmStep` model and invokes `npm run test` (or `npm ci`/`npm install` for install
mode), mapping the exit code.

**Acceptance Scenarios**:

1. **Given** an `npmStep` naming script `test`, **When** it runs, **Then** it invokes
   `npm run test` and maps the exit code.
2. **Given** an `npmStep` in install mode, **When** it runs, **Then** it invokes
   `npm ci` (or `npm install`) as configured.
3. **Given** YAML and Kotlin DSL forms of the same npm step, **When** each is built,
   **Then** they produce the same `NpmStep` model.

### Edge Cases

- The underlying tool is not installed (`gradle`/`docker`/`npm` missing): the step is
  FAILED with a clear, actionable reason — not an unhandled crash.
- A `gradleStep` with no `./gradlew` and no system `gradle`: FAILED with guidance.
- Tool-specific non-zero exit codes map to FAILED with the code surfaced.
- A typed step and an equivalent `run("…")` step produce the same observable lifecycle
  and isolation guarantees (the seam treats them uniformly).
- Mixed pipelines (typed steps and `run` steps in the same stage) execute correctly.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a `GradleStep` step type capturing tasks, args, and
  whether to prefer the Gradle wrapper; its executor MUST invoke Gradle accordingly
  and map the exit code to step status.
- **FR-002**: System MUST provide a `DockerStep` step type supporting at least
  `docker run` (image + command) and `docker build` (context/dockerfile/tag) modes;
  its executor MUST invoke the `docker` CLI accordingly and map the exit code.
- **FR-003**: System MUST provide an `NpmStep` step type supporting script execution
  (`npm run <script>`) and install (`npm ci`/`npm install`); its executor MUST invoke
  npm accordingly and map the exit code.
- **FR-004**: Each new step type MUST be a `StepDefinition` subtype with a
  corresponding `StepExecutor`, registered into the engine's executor registry from
  feature 001 — the engine's stage/step loop MUST NOT change (FR-016 of 001).
- **FR-005**: Each step type MUST be expressible in **both** the YAML descriptor
  (typed keys `gradle:` / `docker:` / `npm:` alongside `run:`) and the Kotlin DSL
  (`gradleStep { … }`, `dockerStep { … }`, `npmStep { … }`), producing the same model.
- **FR-006**: Typed steps MUST inherit the v0 guarantees uniformly via `StepContext`:
  isolated working directory + scoped environment, per-step timeout, secret masking in
  logs, and the sealed-class status lifecycle.
- **FR-007**: When the underlying tool binary is absent or unrunnable, the step MUST be
  FAILED with an actionable message identifying the missing tool.
- **FR-008**: The Kotlin DSL builders MUST be generated through the existing
  Konstellation meta-DSL setup (the `@KontinuanceDsl` marker + `DslBuilder<T>` pattern
  and KSP config in the `dsl` module), not hand-written.

### Key Entities

- **GradleStep**: tasks, args, useWrapper flag → `StepDefinition`.
- **DockerStep**: mode (run/build), image, command, build context/dockerfile/tag,
  volume/env options → `StepDefinition`.
- **NpmStep**: mode (script/install), script name, package-manager flags →
  `StepDefinition`.
- **StepExecutor (per type)**: `GradleStepExecutor`, `DockerStepExecutor`,
  `NpmStepExecutor` — each `supports` its type and `execute`s via the tool CLI.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can express a Gradle/Docker/NPM step without writing a raw
  shell string, in both YAML and the Kotlin DSL.
- **SC-002**: For each step type, the YAML and Kotlin DSL forms produce identical
  models in 100% of cases.
- **SC-003**: Adding these three step types requires **zero** changes to the feature
  001 engine execution loop (only new subtypes + executors + registration).
- **SC-004**: A missing tool binary yields a FAILED step naming the tool in 100% of
  cases (never an unhandled exception).
- **SC-005**: Typed steps and equivalent `run` steps show identical isolation and
  secret-masking behavior.

## Assumptions

- Depends on feature `001-pipeline-foundation` being implemented (the `StepDefinition`
  / `StepExecutor` seam and `StepContext`).
- Still in-process; `dockerStep` invokes the host `docker` CLI (tool invocation), not
  Docker-based runner isolation (a v1 concern).
- Tool binaries (`gradle`/`gradlew`, `docker`, `npm`) are available on the runner host;
  absence is handled as a FAILED step, not a platform error.
- The three types are independent slices and can be delivered incrementally (Gradle
  first), each independently testable.
