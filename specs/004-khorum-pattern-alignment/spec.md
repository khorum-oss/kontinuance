# Feature Specification: Khorum Pattern Alignment

**Feature Branch**: `004-khorum-pattern-alignment`

**Created**: 2026-07-15

**Status**: Draft

**Input**: User description: "Align kontinuance's build tooling and Spec Kit conventions with the khorum-oss/relikquary reference patterns (the 'everything portable' alignment set): fix Kover/SonarCloud coverage aggregation to measure the real code modules; add a shared config/detekt/detekt.yml; adopt the dependency.env public/private repository switch; add a dedicated integration-tests module; add per-feature checklists/ directories; bump Kotlin 2.1.20 → 2.3.21; and housekeeping (remove the stray micronautVersion, add .kotlin/** to .gitignore, refresh the CLAUDE.md pointer). Out of scope: removing kontinuance-native strengths (khorum.* plugins, KSP/Konstellation, geordi/core-test, docs/ tree, docs/roadmap.md, git Spec Kit extension)."

## User Scenarios & Testing *(mandatory)*

Kontinuance and its sibling khorum app, Relikquary, share one org toolchain (Kotlin/Gradle, detekt/Kover/SonarCloud, a version catalog, and the Spec Kit workflow). Kontinuance began from a template and has drifted from the conventions Relikquary has since matured. This feature closes the portable gaps so a developer moving between the two repos finds the same build ergonomics, and so the quality gates actually protect the code. The "users" are kontinuance maintainers and the automated quality gates that run on every change.

### User Story 1 - Quality gates measure the real code (Priority: P1)

A maintainer opens a pull request. The coverage and static-analysis gates report numbers for the module that actually holds the engine code, not for a near-empty scaffolding module. A regression in the engine's test coverage is visible and can block the merge.

**Why this priority**: Today the aggregated coverage report and the SonarCloud coverage path both point at the `dsl` module, which is a near-empty stub — essentially all production code lives in `engine`. The gate therefore reports coverage for code that barely exists and is blind to the engine. This silently defeats Constitution Principle III (Quality Gates Are Non-Negotiable). Fixing it is the highest-value change and a prerequisite for trusting every later one.

**Independent Test**: Run the aggregated coverage report and confirm it includes `engine` (and every other coverage-bearing module); introduce an uncovered engine branch and confirm the coverage number drops and the SonarCloud-consumed report reflects it.

**Acceptance Scenarios**:

1. **Given** the aggregated coverage task, **When** it runs, **Then** its report includes coverage contributed by the `engine` module (and any other module that ships production code), not only `dsl`.
2. **Given** the SonarCloud coverage configuration, **When** analysis runs, **Then** the report path it reads contains the aggregated, engine-inclusive coverage.
3. **Given** a newly added, untested code path in `engine`, **When** the coverage gate runs, **Then** the measured coverage decreases relative to before.

### User Story 2 - One shared, discoverable code-style configuration (Priority: P2)

A maintainer wants to know or adjust the project's lint rules. They find a single shared detekt configuration file at a conventional location, and every module is wired to it, exactly as in Relikquary.

**Why this priority**: Relikquary keeps a central `config/detekt/detekt.yml` that each module references; kontinuance has no detekt config file at all and instead repeats inline settings per module. A shared file makes deviations explicit and consistent, but the build is already green, so this is important-not-urgent.

**Independent Test**: Confirm a single shared detekt config exists at the conventional path, each module references it, and detekt still runs clean across all modules.

**Acceptance Scenarios**:

1. **Given** the repository, **When** a maintainer looks for the lint rules, **Then** a single shared detekt configuration file exists at the conventional `config/detekt/` location.
2. **Given** any module's detekt task, **When** it runs, **Then** it applies the shared configuration and completes with zero violations.

### User Story 3 - Environment-switchable dependency resolution (Priority: P2)

A maintainer (or CI) can select where dependencies and plugins resolve from — the internal proxy for normal development, or public repositories for public/CI builds — via a single property, matching Relikquary's `dependency.env` convention.

**Why this priority**: Relikquary gates repository declarations on a `dependency.env` property (with a proxy location) and flips to public repositories in CI. Kontinuance hardcodes its repositories with no switch. Aligning this makes builds portable across the internal and public environments and mirrors the org convention; it touches settings, the root build, properties, and CI, so it carries more risk than Story 2.

**Independent Test**: Build with the default (internal) selection and with the public selection; confirm both resolve successfully and that CI uses the public selection.

**Acceptance Scenarios**:

1. **Given** the default environment selection, **When** a build runs, **Then** dependencies and plugins resolve through the internal/default configuration without requiring the public flag.
2. **Given** the public environment selection, **When** a build runs, **Then** dependencies and plugins resolve from public repositories.
3. **Given** the CI pipeline, **When** it builds the project, **Then** it selects the public environment.

### User Story 4 - A dedicated home for cross-cutting integration tests (Priority: P3)

A maintainer adds a heavier, cross-module or externally-dependent integration test. It lives in a dedicated `integration-tests` module (as in Relikquary), separate from fast unit tests, and its coverage still counts toward the aggregate.

**Why this priority**: Relikquary isolates Testcontainers-style/cross-cutting integration tests in their own Gradle module so they can grow and be tuned independently. Kontinuance houses everything under `engine/src/test`. The split is a structural convention improvement, valuable but not blocking, and only pays off as such tests appear.

**Independent Test**: A representative integration test placed in the new module runs via the standard test task and its coverage over production modules is reflected in the aggregate report.

**Acceptance Scenarios**:

1. **Given** the build, **When** modules are listed, **Then** a dedicated `integration-tests` module exists and produces no runnable/publishable artifact.
2. **Given** an integration test in that module, **When** the test task runs, **Then** it executes and its coverage over the production modules is included in the aggregated report.

### User Story 5 - Spec Kit convention parity (Priority: P3)

A maintainer browsing the specs finds the same artifact set the sibling repo uses — including a `checklists/` directory per feature — and the agent-context pointer reflects the feature currently being worked, not a stale one.

**Why this priority**: Relikquary's every feature carries a `checklists/` directory and its agent-context pointer tracks the current feature. Kontinuance omits `checklists/` and its pointer is stuck on the first feature. This is low-risk documentation/workflow parity.

**Independent Test**: Confirm the current feature has a `checklists/` directory and that the agent-context pointer references the feature under active work.

**Acceptance Scenarios**:

1. **Given** the active feature directory, **When** a maintainer inspects it, **Then** it contains a `checklists/` directory consistent with the sibling repo's convention.
2. **Given** the agent-context file, **When** a maintainer reads it, **Then** its pointer references the feature currently under work rather than a stale earlier one.

### User Story 6 - Current toolchain and a clean build surface (Priority: P3)

A maintainer reads the build configuration and finds it free of unused, misleading settings, on a current language toolchain, and ignoring the right build artifacts.

**Why this priority**: The Kotlin version trails Relikquary's; `gradle.properties` carries a stray `micronautVersion` for a framework the project does not use; and `.gitignore` misses a Kotlin build-output directory. These are trivial hygiene items with low risk and modest payoff.

**Independent Test**: Confirm the language toolchain matches the target version and the build succeeds; confirm the unused property is gone and the ignore rule is present.

**Acceptance Scenarios**:

1. **Given** the version catalog, **When** a maintainer checks the language version, **Then** it matches the agreed target and the full build succeeds on it.
2. **Given** `gradle.properties`, **When** a maintainer reads it, **Then** it contains no settings for frameworks the project does not use.
3. **Given** `.gitignore`, **When** a Kotlin build runs, **Then** its transient output directory is ignored.

### Edge Cases

- What happens when the public environment selection is chosen but an internal-only artifact is required? The build must fail with a clear resolution error rather than silently mixing sources.
- How does the coverage aggregate behave for a module that legitimately has no production code (e.g., a pure test-support module)? It must be either included harmlessly or deliberately excluded, never the sole measured module.
- What happens to existing inline detekt settings when the shared config is introduced? Behavior must not regress — the effective rule set stays at least as strict, with zero new violations.
- What happens to the Kotlin version bump if it surfaces new compiler warnings/errors or KSP/Konstellation incompatibilities? The bump is only complete when the full build and all tests pass on the new version.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The aggregated coverage report MUST include coverage from every module that ships production code (at minimum `engine`), not only the `dsl` module.
- **FR-002**: The SonarCloud coverage configuration MUST read the aggregated, engine-inclusive coverage report.
- **FR-003**: A single shared detekt configuration MUST exist at the conventional `config/detekt/` location, and every module's detekt task MUST reference it while still completing with zero violations.
- **FR-004**: The build MUST support selecting the dependency/plugin resolution environment via a single property (matching the sibling repo's `dependency.env` convention), with a defined default for local development and a public selection for CI/public builds.
- **FR-005**: The CI pipeline MUST build using the public environment selection.
- **FR-006**: The build MUST include a dedicated `integration-tests` module that produces no runnable/publishable artifact and whose coverage over production modules is included in the aggregate.
- **FR-007**: The active feature MUST include a `checklists/` directory consistent with the sibling repo's per-feature artifact set.
- **FR-008**: The agent-context pointer MUST reference the feature currently under active work rather than a stale earlier feature.
- **FR-009**: The language toolchain version MUST be updated to the agreed target, and the full build and test suite MUST pass on it.
- **FR-010**: `gradle.properties` MUST NOT declare configuration for frameworks the project does not use (specifically the unused `micronautVersion`).
- **FR-011**: `.gitignore` MUST ignore the Kotlin build-output directory used by the toolchain.
- **FR-012**: The change MUST NOT remove or weaken kontinuance-native capabilities that are out of scope: the `khorum.*` Gradle plugins, KSP/Konstellation code generation, the geordi/`core-test` framework, the `docs/` tree, `docs/roadmap.md`, and the git Spec Kit extension.
- **FR-013**: All existing quality gates (detekt zero-violations, Kover verification, no SonarCloud regression) MUST continue to pass after the alignment, per Constitution Principle III.

### Key Entities

- **Coverage aggregate**: the single, root-level report that combines per-module coverage; consumed by the SonarCloud gate. Must span all production modules.
- **Shared detekt configuration**: one canonical rule-set file referenced by every module.
- **Dependency environment selector**: a single property whose value chooses internal vs. public resolution of dependencies and plugins.
- **Integration-tests module**: a non-publishable Gradle module hosting cross-cutting/heavier tests, contributing to the coverage aggregate.
- **Per-feature checklist set**: the `checklists/` artifact directory attached to a Spec Kit feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of production modules (at minimum `engine`) contribute to the aggregated coverage report, and the SonarCloud gate reads that aggregate (verified by an intentional uncovered engine path lowering the reported number).
- **SC-002**: There is exactly one shared detekt configuration file, referenced by 100% of modules that run detekt, with zero detekt violations across the build.
- **SC-003**: The project builds successfully under both the default and the public dependency-environment selections, and CI uses the public selection.
- **SC-004**: A representative integration test in the dedicated module runs green and its coverage is counted in the aggregate.
- **SC-005**: The full build and complete test suite pass on the updated language toolchain version.
- **SC-006**: The build configuration contains zero settings for unused frameworks, and the Kotlin build-output directory is ignored by version control.
- **SC-007**: Every out-of-scope kontinuance-native capability listed in FR-012 remains present and functional after the change (0 removed).

## Assumptions

- The internal dependency proxy and the sibling repo's `dependency.env` values (e.g., an internal default such as `stage`, and `public` for CI) are the intended model; the default local selection resolves through the existing internal/shared repositories kontinuance uses today.
- "Current toolchain target" for the Kotlin bump is the sibling repo's version (2.3.21); if that version proves incompatible with the pinned KSP/Konstellation stack, the bump is deferred and recorded rather than forcing an incompatible upgrade.
- The `engine` module is the primary production module today; `core-test` is a test-support module and `dsl` is currently near-empty scaffolding. Aggregation must be correct regardless of the pending `engine→dsl` refactor.
- CI continues to consume the existing centralized khorum reusable workflows; adopting `dependency.env` means those workflows (or their invocation) pass the public flag, not that CI is rewritten.
- This feature is tooling/convention alignment only; it changes no runtime behavior of the pipeline engine and no consumer-facing pipeline contract.
