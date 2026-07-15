# Implementation Plan: Khorum Pattern Alignment

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` (work stays here; `004` numbers the specs dir only) | **Date**: 2026-07-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-khorum-pattern-alignment/spec.md`

## Summary

Close the *portable* convention gaps between kontinuance and its sibling khorum app,
`khorum-oss/relikquary`, without stripping kontinuance-native strengths. The highest-value change
repairs a broken quality gate: root Kover aggregation and the SonarCloud coverage path currently
point at the near-empty `dsl` module, so coverage is effectively blind to `engine` (where all
production code lives). Remaining changes adopt relikquary's shared `config/detekt/detekt.yml`, its
`dependency.env` public/private resolution switch, a dedicated `integration-tests` module, Spec Kit
per-feature `checklists/`, a Kotlin toolchain bump, and small housekeeping. The build must stay
green throughout (Constitution Principle III).

## Technical Context

**Language/Version**: Kotlin (currently `2.1.20`, target `2.3.21`) on the JDK 21 toolchain.

**Primary Dependencies**: Gradle Kotlin DSL build; detekt `1.23.6`, Kover `0.9.1`, SonarQube plugin
`7.0.0.6105` (quality gates); KSP `2.1.20-1.0.32` and Konstellation (`meta-dsl 1.0.15`,
`dsl 2.0.14`) for the generated DSL; the `khorum.*` Gradle plugin suite; `snakeyaml-engine`. The
Kotlin bump is coupled to KSP and Konstellation compatibility (see research.md R1).

**Storage**: N/A — this is build-tooling/convention work; no runtime data.

**Testing**: JUnit Jupiter + MockK + the geordi/`core-test` framework for existing module tests; a
new `integration-tests` module runs on the JUnit platform and contributes to the coverage aggregate.
`./gradlew build` (compile + detekt + Kover verify + tests) is the acceptance harness.

**Target Platform**: Linux/JVM developer and CI build (the artifact is the build itself, not a new runtime).

**Project Type**: Multi-module JVM build — convention/tooling alignment across the existing
`core-test` / `dsl` / `engine` modules plus one new `integration-tests` module.

**Performance Goals**: None beyond "no build-time regression"; the gate changes must not materially
slow `./gradlew build`.

**Constraints**: No quality gate may be disabled, baselined, or weakened (Principle III). Dependency
verification (`gradle/verification-metadata.xml`) MUST stay enabled and be updated for any
changed/added artifact (Principle V). All work stays on the designated branch. Out-of-scope
kontinuance-native capabilities (FR-012) must remain intact.

**Scale/Scope**: 3 modules → 4. Touches: root `build.gradle.kts`, `settings.gradle.kts`,
`gradle.properties`, `gradle/libs.versions.toml`, per-module `build.gradle.kts` detekt blocks, a new
`config/detekt/detekt.yml`, the new `integration-tests` module, `.github/workflows/*.yml` (or their
reusable-workflow invocation), `.gitignore`, `CLAUDE.md`, and `gradle/verification-metadata.xml`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against the Kontinuance Constitution v1.1.0:

- **I. Platform-First & Stable Public Contract** — PASS. No change to the pipeline DSL, public API,
  or `VERSION`; this is build-tooling only and touches no consumer-facing contract.
- **II. Test-First & Integration-Verified Discipline** — PASS / advanced. The new `integration-tests`
  module operationalizes the constitution's `@SpringBootTest`+Testcontainers home for cross-cutting
  ITs. The coverage-aggregation fix is validated by a failing-first check (an intentionally uncovered
  `engine` path must lower the reported number — proving the gate now sees `engine`).
- **III. Quality Gates Are Non-Negotiable** — PASS / this feature *repairs* the gate. Coverage moves
  from measuring the empty `dsl` stub to the real modules; detekt gains a shared config; no gate is
  weakened or baselined. The `@ExcludeFromCoverage` convention is preserved.
- **IV. Correct, Covered Code Generation** — PASS (with a gate). The Kotlin bump must keep KSP +
  Konstellation generation compiling and test-covered; if a compatible KSP/Konstellation release does
  not exist, the bump is deferred and recorded (research.md R1), never forced by hand-editing
  generated output.
- **V. Supply-Chain Integrity & Reproducible Publishing** — PASS (with a task). Any changed/added
  dependency (Kotlin/KSP bump, integration-test deps) MUST be reflected in
  `verification-metadata.xml` with verification kept enabled; no secrets are added.

**Result: PASS** — no violations; Complexity Tracking left empty. Re-checked after Phase 1: still
PASS (adds one module and config files, reuses existing tooling; no new architectural complexity).

## Project Structure

### Documentation (this feature)

```text
specs/004-khorum-pattern-alignment/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output — decisions (Kotlin/KSP compat, dependency.env shape, aggregation approach)
├── data-model.md        # Phase 1 output — the build-config "entities" and their invariants
├── quickstart.md        # Phase 1 output — how to validate the alignment end-to-end
├── contracts/           # Phase 1 output — the build/CI contract (dependency.env, module list, coverage path, detekt config)
│   └── build-contract.md
├── checklists/
│   └── requirements.md  # created by /speckit-specify
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
config/
└── detekt/
    └── detekt.yml            # NEW — shared detekt ruleset, referenced by every module

integration-tests/           # NEW module (registered via includeModules in settings.gradle.kts)
├── build.gradle.kts
└── src/test/kotlin/org/khorum/oss/kontinuance/integration/

engine/                      # existing — detekt block gains config.setFrom(...); coverage now aggregated
dsl/                         # existing — detekt block gains config.setFrom(...)
core-test/                   # existing — test-support module

build.gradle.kts             # root — Kover aggregation over all production modules; Sonar path → root report; dependency.env-gated repos
settings.gradle.kts          # register integration-tests; dependency.env-gated pluginManagement repos
gradle.properties            # add dependency.env + proxy.location; drop unused micronautVersion
gradle/libs.versions.toml    # Kotlin (+ KSP) bump; align logging artifact
gradle/verification-metadata.xml  # regenerated for changed/added artifacts
.github/workflows/*.yml      # pass -Pdependency.env=public where builds run
.gitignore                   # add .kotlin/**
CLAUDE.md                    # SPECKIT pointer → this plan
```

**Structure Decision**: Keep the existing multi-module JVM layout; add exactly one module
(`integration-tests`) registered through the existing `includeModules(...)` helper, plus a top-level
`config/detekt/` directory. No packages move (the pending `engine→dsl` refactor is a separate feature;
aggregation here must be correct for the *current* layout regardless).

## Complexity Tracking

> No Constitution Check violations — section intentionally empty.
