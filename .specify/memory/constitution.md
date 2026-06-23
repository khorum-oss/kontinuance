<!--
SYNC IMPACT REPORT
Version change: 1.0.0 → 1.1.0
Bump rationale: MINOR — project scope clarified as a Spring Boot CI/CD platform and
integration testing elevated to an explicit, first-class requirement. Existing principles are
retained; none removed or made backward-incompatible.
Modified principles:
  I.  Library-First & Stable Public Contract → Platform-First & Stable Public Contract (scope broadened)
  II. Test-First Discipline → Test-First & Integration-Verified Discipline (integration testing added)
Added sections: none (integration-test guidance folded into Principle II, Tech Constraints, Workflow)
Removed sections: none
Templates reviewed:
  ✅ .specify/templates/plan-template.md   — "Constitution Check" gate is principle-agnostic; no edit needed
  ✅ .specify/templates/spec-template.md   — no constitution references; no edit needed
  ✅ .specify/templates/tasks-template.md  — no constitution references; no edit needed
Deferred / follow-up TODOs:
  - Architecture not yet ratified (orchestrator/agent/event-source split, interpreted-vs-codegen
    pipeline DSL, persistence choice per docs/overview.md); to be added in later amendments.
  - RATIFICATION_DATE unchanged (2026-06-22).
-->

# Kontinuance Constitution

Kontinuance is a CI/CD platform written in Kotlin and Spring Boot — an orchestrator, execution
agents, and event sources (webhooks, polling, manual triggers) that run user-defined pipelines.
Pipelines are described through a Kotlin DSL (the `dsl` module, built on the Konstellation
meta-DSL), and `core-test` provides a simulation-based testing framework. The platform integrates
with external systems (source control, container runtimes, databases, message transports), so
those integrations are exercised by real tests. These principles govern how the project is
changed, tested, and released, and will be refined as the architectural decisions in
`docs/overview.md` are made.

## Core Principles

### I. Platform-First & Stable Public Contract

The system is composed of self-contained, independently testable modules with clear
responsibilities (orchestration, execution, event ingestion, the pipeline DSL). The
consumer-facing contracts — the pipeline DSL, public APIs, and any published artifact — MUST NOT
break without a corresponding MAJOR version change. The `VERSION` file is the single source of
truth for the released version and MUST follow Semantic Versioning. Rationale: pipelines authored
against the DSL and clients calling the API depend on stability across versions; silent breakage
strands them.

### II. Test-First & Integration-Verified Discipline

Behavior changes MUST be accompanied by tests, written before the implementation where practical
(Red → Green → Refactor); new or changed public behavior MUST have a failing test demonstrated
before the fix lands. Because the platform's value lies in its integrations (source-control
webhooks, container runtimes, databases, message transports), every integration MUST be covered by
a test that exercises the real boundary — using `@SpringBootTest` with Testcontainers (and
`DynamicPropertySource` for wiring) rather than mocking the dependency away. The project authors
its own test framework (`core-test`/geordi) and holds it to the same standard. Rationale: a CI/CD
system that is not itself integration-tested cannot be trusted to run other teams' builds.

### III. Quality Gates Are Non-Negotiable

The build's automated gates are the enforcement mechanism for these principles and MUST pass on
every change before merge:
- **detekt** static analysis MUST report zero violations (the build fails otherwise).
- **Kover** coverage verification MUST pass; code intentionally exempt from coverage MUST be
  annotated `@ExcludeFromCoverage` rather than silently lowering thresholds.
- **SonarCloud** analysis MUST NOT be regressed by a change.
A gate MUST NOT be disabled, baselined, or weakened to make a change pass; fix the code instead,
or change the gate deliberately through the governance process. Rationale: gates only protect
quality if they cannot be bypassed under deadline pressure.

### IV. Correct, Covered Code Generation

The DSL is produced by KSP code generation driven by the configured `dslMarkerClass`,
`dslBuilderClasspath`, and `projectRootClasspath`. Generated output MUST compile, MUST be
exercised by tests (or explicitly excluded via `@ExcludeFromCoverage` with justification), and
changes to generation logic MUST be validated by regenerating and building, never by editing
generated artifacts by hand. Rationale: generation bugs surface in consumers, not in this repo,
so they must be caught at build time here.

### V. Supply-Chain Integrity & Reproducible Publishing

Dependency verification (checksums and signatures in `gradle/verification-metadata.xml`) MUST
remain enabled; new dependencies are added by extending the verification metadata, never by
disabling verification. Published artifacts MUST be GPG-signed. Secrets (signing keys, Spaces
credentials) MUST be supplied via environment variables or untracked local files and MUST NEVER
be committed. Rationale: a signed, verifiable library is the baseline trust contract for anyone
who depends on it.

## Technology & Standards Constraints

- **Language/Toolchain**: Kotlin on the JDK 21 toolchain across all modules. Build logic is
  authored in Gradle Kotlin DSL (`*.gradle.kts`).
- **Runtime**: the platform runs on Spring Boot (Kotlin); asynchronous coordination uses Kotlin
  coroutines, and external-system clients and persistence are wired through Spring configuration.
- **Integration testing**: integration tests use `@SpringBootTest` + Testcontainers and MUST NOT
  depend on shared external services or hard-coded ports (use `DynamicPropertySource`).
- **Module boundaries**: the platform is split by responsibility (e.g. orchestrator, agent/runner,
  event sources, the `dsl` pipeline language); `core-test` is the testing framework consumed by the
  others' tests. New modules MUST be registered through `settings.gradle.kts`.
- **Dependencies**: declared through the Gradle version catalog (`gradle/libs.versions.toml`); a
  new or upgraded dependency MUST be reflected in `verification-metadata.xml`.
- **Conventions**: code MUST satisfy detekt's configured ruleset (package/class naming, trailing
  newline, etc.); template placeholder tokens MUST NOT remain in shipped sources.

## Development Workflow & Quality Gates

- Feature work follows Spec-Driven Development via Spec Kit: `/speckit-specify` →
  (`/speckit-clarify`) → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`, with
  `/speckit-analyze` and `/speckit-checklist` used to harden specs as needed.
- `./gradlew build` (compile + detekt + Kover verification + tests) MUST pass locally before a
  change is proposed for merge.
- A change that touches an external integration MUST include a passing integration test
  (`@SpringBootTest` + Testcontainers); an integration change without one MUST be rejected.
- Every change is reviewed; the reviewer MUST confirm the build is green and that these
  principles are upheld, justifying any exception in the PR.
- Publishing is a deliberate, signed release of the version named in `VERSION`; it MUST NOT
  happen as a side effect of routine development.

## Governance

This constitution supersedes ad-hoc practice. When a principle and convenience conflict, the
principle wins or the constitution is amended — it is not quietly ignored.

- **Amendments**: proposed via PR that edits this file, states the rationale, and bumps the
  version below per Semantic Versioning — MAJOR for principle removal/redefinition, MINOR for a
  new principle or materially expanded guidance, PATCH for clarifications.
- **Versioning policy**: the constitution version is independent of the library `VERSION`.
- **Compliance review**: every PR review verifies the change complies with these principles;
  unjustified gate bypasses are grounds to reject. Use `CLAUDE.md` and the active plan for
  day-to-day runtime guidance.

**Version**: 1.1.0 | **Ratified**: 2026-06-22 | **Last Amended**: 2026-06-22
