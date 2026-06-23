# Implementation Plan: Pipeline Execution Foundation (v0)

**Branch**: `001-pipeline-foundation` | **Date**: 2026-06-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-pipeline-foundation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Deliver the v0 foundation of the Kontinuance CI/CD platform: define a pipeline
either through a declarative YAML/TOML descriptor or an equivalent Kotlin DSL
escape hatch (Option C hybrid, built on the latest Konstellation meta-DSL), and
execute it **in a single local process**. A coroutine-coordinated engine runs
stages and steps in declared order, launching shell commands via `ProcessBuilder`
inside isolated working directories with scoped environments, enforcing per-step
timeouts and a concurrency cap, streaming per-step logs to stdout with secret
masking, and tracking lifecycle through an explicit sealed-class status model.
Stateless: no Docker, persistence, webhooks, or UI (deferred to v1+).

## Technical Context

**Language/Version**: Kotlin 2.1.20 on the JDK 21 toolchain (matches existing modules).

**Primary Dependencies**: Kotlin Coroutines (`kotlinx-coroutines-core` 1.10.0,
already in the catalog) for structured concurrency; the latest Konstellation
meta-DSL for the Kotlin pipeline DSL (pinned `konstellation-meta-dsl = 1.0.15`,
`konstellation-dsl = 2.0.14` — note the 2.x artifactId rename, see research.md R1);
`kotlinx-serialization`
(already present) or a YAML library for descriptor parsing (decision in research.md);
Spring Boot is the platform runtime per the constitution but is **not required for the
v0 engine core** and is introduced minimally (decision in research.md).

**Storage**: N/A for v0 (stateless; logs stream to stdout, working dirs are temp and
cleaned up). Postgres persistence is deferred to v1.

**Testing**: JUnit Jupiter + MockK plus the project's own `core-test`/geordi
simulation framework (per Constitution Principle II); integration-style tests that
exercise real `ProcessBuilder` execution against a temp filesystem.

**Target Platform**: Linux/JVM server (single process).

**Project Type**: Multi-module JVM library/platform — adds an `engine` module to the
existing Gradle multi-module build (`dsl`, `core-test`).

**Performance Goals**: Timeout enforcement terminates an overrunning step within ~1s
of its deadline (SC-005); never exceed the configured concurrency cap K (SC-006).
No throughput target for v0.

**Constraints**: Single process, no external services; step isolation is
working-directory + scoped-env + process-tree-cleanup based (not container based);
secret values never appear unmasked in logs.

**Scale/Scope**: A handful of pipelines, each with a modest number of stages/steps,
run locally; v0 is a correctness and architecture-shaping milestone, not a scaling one.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against the Kontinuance Constitution v1.1.0:

- **I. Platform-First & Stable Public Contract** — PASS. v0 introduces the
  consumer-facing pipeline definition contract (YAML schema + Kotlin DSL). It is a
  new contract (no break). The pipeline model is designed as the stable seam both
  front-ends produce and the engine consumes. `VERSION` is unchanged by spec/plan/tasks.
- **II. Test-First & Integration-Verified Discipline** — PASS (enforced in tasks).
  Behavior changes ship with tests written first (Red→Green). The platform's value is
  execution, so step execution is exercised against the **real boundary**
  (`ProcessBuilder` + a real temp filesystem), not mocked away — the v0 analogue of
  the constitution's Testcontainers requirement (no external integrations exist yet in
  v0, so there is nothing to Testcontainer; this is recorded, not waived). `core-test`
  is used and held to the same standard.
- **III. Quality Gates Are Non-Negotiable** — PASS (enforced in tasks). detekt zero
  violations, Kover verification passes (exemptions only via `@ExcludeFromCoverage`
  with justification), SonarCloud not regressed. No gate weakened.
- **IV. Correct, Covered Code Generation** — N/A for the engine; PASS where it
  touches the DSL. v0 uses the Konstellation meta-DSL as the DSL mechanism; any KSP
  generation involved must compile and be test-covered, and generated artifacts are
  never hand-edited.
- **V. Supply-Chain Integrity & Reproducible Publishing** — PASS (enforced in tasks).
  New/upgraded dependencies (Konstellation bump, YAML/serialization, any Spring Boot)
  are added through `gradle/libs.versions.toml` and reflected in
  `gradle/verification-metadata.xml`; verification stays enabled; no secrets committed.

**Result: PASS** — no violations; Complexity Tracking left empty. Re-checked after
Phase 1 design: still PASS (the design adds one module and reuses existing tooling;
no new architectural complexity requiring justification).

## Project Structure

### Documentation (this feature)

```text
specs/001-pipeline-foundation/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── pipeline-descriptor.schema.md   # YAML/TOML pipeline descriptor contract
│   └── dsl-and-engine-api.md           # Kotlin DSL + engine entry-point contract
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

A new `engine` module is added to the existing multi-module Gradle build alongside
`dsl` and `core-test`. The engine holds the pipeline model, both definition
front-ends, and the in-process execution core.

```text
engine/
├── build.gradle.kts
└── src/
    ├── main/kotlin/org/khorum/oss/kontinuance/engine/
    │   ├── model/        # Pipeline, Stage, Step, Run, PipelineStatus (sealed), Secret
    │   ├── dsl/          # Kotlin DSL (Option C escape hatch) on Konstellation meta-DSL
    │   ├── descriptor/   # YAML/TOML descriptor parsing → pipeline model
    │   ├── execution/    # coroutine engine, ProcessBuilder step runner, timeout,
    │   │                 # concurrency Semaphore, working-dir isolation + cleanup
    │   ├── logging/      # stdout log streaming + secret masking
    │   └── secret/       # secret abstraction (v0 env-var backing)
    └── test/kotlin/org/khorum/oss/kontinuance/engine/
        ├── ...           # unit + real-ProcessBuilder execution tests (geordi/JUnit)

dsl/        # existing — Konstellation-based DSL building blocks (reused)
core-test/  # existing — geordi simulation test framework (reused)
settings.gradle.kts   # register the new "engine" module via includeModules(...)
gradle/libs.versions.toml          # bump Konstellation; add YAML/Spring deps
gradle/verification-metadata.xml    # add verification entries for new/upgraded deps
```

**Structure Decision**: Single multi-module JVM build (Option: multi-module library/
platform). v0 adds exactly one module, `engine`, registered in `settings.gradle.kts`
through the existing `includeModules(...)` helper. The orchestrator/agent/event-source
split from the overview is honored as **packages/seams within `engine`** for v0 (one
process), so they can later be extracted into separate modules without reshaping the
public pipeline contract. `dsl` and `core-test` are reused as-is.

## Complexity Tracking

> No Constitution Check violations — section intentionally empty.
