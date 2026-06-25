# Phase 0 Research: Typed Step Types (Gradle / Docker / NPM)

## R1 — Build on the 001 seam, not a parallel mechanism

- **Decision**: Each type is a `StepDefinition` subtype + a `StepExecutor` registered
  into the feature-001 registry; reuse `StepContext` for workdir/env/timeout/masking.
- **Rationale**: FR-016 of 001 exists for exactly this; keeps the engine loop untouched
  (SC-003) and gives typed steps the same guarantees as `run` for free.
- **Alternatives**: A separate "plugin" mechanism — rejected as premature (the seam
  already suffices); subclassing `RunStep` — rejected (each tool needs distinct config).

## R2 — Tool invocation strategy (CLI shell-out)

- **Decision**: Executors build an argv and launch the tool via the same
  `ProcessBuilder` path the `RunStep` executor uses. Gradle prefers `./gradlew` when
  present (configurable); docker/npm use the host binary on `PATH`.
- **Rationale**: Matches v0's in-process model; no heavyweight SDK (e.g. docker-java)
  needed for tool invocation, keeping the dependency/verification surface flat.
- **Alternatives**: `docker-java`/Gradle Tooling API — more power but heavier deps and
  ops; deferred until a concrete need (e.g. v1 container isolation) appears.

## R3 — Missing-tool handling

- **Decision**: Before/at launch, a missing or unrunnable binary maps to a FAILED step
  with a message naming the tool and how to install it (FR-007, SC-004) — never an
  unhandled exception.
- **Rationale**: CI hosts vary; a clear failure is far more useful than a stack trace.

## R4 — DSL generation via Konstellation

- **Decision**: Builders (`gradleStep`/`dockerStep`/`npmStep`) follow the `dsl` module
  pattern: `@KontinuanceDsl` marker + a `DslBuilder<T>` interface per type, with KSP
  (Konstellation) generating the concrete builder. KSP args already configured in
  `dsl/build.gradle.kts` (`dslMarkerClass`/`dslBuilderClasspath`/`projectRootClasspath`).
- **Rationale**: FR-008 + Constitution Principle IV; no hand-written builders.
- **Reference**: `dsl/src/main/kotlin/org/khorum/oss/kontinuance/dsl/common/{KontinuanceDsl,DslBuilder}.kt`.

## R5 — YAML descriptor extension

- **Decision**: Add typed step keys to the descriptor parser: a step object may carry
  exactly one of `run:` / `gradle:` / `docker:` / `npm:`; the parser maps each to its
  `StepDefinition` subtype. Validation rejects a step that declares more than one or
  none.
- **Rationale**: Keeps the descriptor uniform with the model and the Kotlin DSL (FR-005).

## R6 — Delivery order

- **Decision**: Gradle (US1) first as the MVP of this feature, then Docker (US2), then
  NPM (US3); each is an independent, testable slice.
- **Rationale**: Matches the primary Kotlin/Gradle audience and the seam makes the
  three independent.
