# Implementation Plan: First-Class Gradle / Docker / NPM Step Types

**Branch**: `002-typed-steps` | **Date**: 2026-06-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-typed-steps/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Add three first-class, configured step types — `gradleStep`, `dockerStep`, `npmStep` —
to the pipeline DSL, built on the feature 001 step-type seam (sealed `StepDefinition`
+ `StepExecutor` registry, FR-016). Each is a new `StepDefinition` subtype with a
matching executor that shells out to the tool's CLI (`gradle`/`gradlew`, `docker`,
`npm`) and maps the exit code to the existing status model. Both definition
front-ends gain the type: the YAML descriptor gets `gradle:`/`docker:`/`npm:` keys and
the Kotlin DSL gets typed builders generated through the existing Konstellation
meta-DSL setup. The engine's stage/step loop is **untouched** — this is purely
additive registration.

## Technical Context

**Language/Version**: Kotlin 2.1.20 / JDK 21 (matches 001 + existing modules).

**Primary Dependencies**: feature 001's `engine` module (the seam, `StepContext`,
masking, status); the Konstellation meta-DSL (`konstellation-meta-dsl = 1.0.15`,
`konstellation-dsl = 2.0.14`) + KSP for generated builders; no new third-party runtime
deps expected (executors invoke host CLIs via `ProcessBuilder`).

**Storage**: N/A (inherits v0 statelessness).

**Testing**: JUnit Jupiter + MockK + `core-test`/geordi. Executor tests assert correct
CLI command construction (pure, fast) and, where the tool is present, exercise the real
binary; real-binary execution is environment-gated so the suite stays green without
`docker`/`npm` installed.

**Target Platform**: Linux/JVM runner host with the relevant tool binaries available.

**Project Type**: Extends the `engine` module from 001 (and uses the `dsl` module's
Konstellation builder pattern); no new module.

**Performance Goals**: None beyond 001. **Constraints**: in-process; `dockerStep`
invokes the host `docker` CLI (tool invocation, not runner isolation).

**Scale/Scope**: Three step types, each an independent slice (Gradle first).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Against Constitution v1.1.0:
- **I. Platform-First & Stable Public Contract** — PASS. Adds new typed DSL/descriptor
  surface (additive, no break). Built as stable `StepDefinition` subtypes.
- **II. Test-First & Integration-Verified Discipline** — PASS (enforced in tasks).
  Each executor is test-first; real-boundary tests invoke the actual tool CLI where
  available (the v0-style real boundary), gated so absence doesn't fail the suite.
- **III. Quality Gates Are Non-Negotiable** — PASS. detekt/Kover/Sonar unchanged and
  must stay green; generated builders covered or `@ExcludeFromCoverage` with reason.
- **IV. Correct, Covered Code Generation** — PASS. DSL builders are KSP-generated via
  Konstellation (FR-008); generated output compiles and is test-exercised, never
  hand-edited.
- **V. Supply-Chain Integrity & Reproducible Publishing** — PASS. No new external deps
  anticipated; any that appear go through the catalog + `verification-metadata.xml`.

**Result: PASS** — no violations; Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-typed-steps/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── typed-steps-descriptor.schema.md   # YAML keys gradle:/docker:/npm:
│   └── typed-steps-dsl.md                  # gradleStep/dockerStep/npmStep builders
└── tasks.md
```

### Source Code (repository root)

Additive within the existing `engine` module from 001 (builders leverage the `dsl`
module's Konstellation pattern):

```text
engine/src/main/kotlin/org/khorum/oss/kontinuance/engine/
├── model/steps/        # GradleStep, DockerStep, NpmStep (StepDefinition subtypes)
├── execution/steps/    # GradleStepExecutor, DockerStepExecutor, NpmStepExecutor
├── dsl/steps/          # gradleStep/dockerStep/npmStep builders (Konstellation/KSP)
└── descriptor/steps/   # YAML mapping for gradle:/docker:/npm: keys
```

**Structure Decision**: No new module — these are new packages inside `engine`, plus
generated builders following the `dsl` module's `@KontinuanceDsl` + `DslBuilder<T>`
pattern. Each type registers its executor into the 001 registry at engine init.

## Complexity Tracking

> No Constitution Check violations — section intentionally empty.
