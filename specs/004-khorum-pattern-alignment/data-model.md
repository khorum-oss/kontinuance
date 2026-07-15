# Phase 1 Data Model: Khorum Pattern Alignment

This feature has no runtime data. Its "entities" are **build-configuration artifacts** and their
invariants — the things a change must keep true. Listed so tasks and review have a checkable model.

## Entities

### Coverage aggregate
- The single root-level Kover report combining per-module coverage.
- `members`: the set of production modules aggregated — MUST include `engine` (and `dsl`, and
  `integration-tests` once present); MUST exclude the test-support module `core-test`.
- `report_path`: the root report XML that SonarCloud reads.
- **Invariant**: an uncovered path added to `engine` lowers the aggregate number (the gate sees `engine`).

### Shared detekt configuration
- One file at `config/detekt/detekt.yml`, layered on defaults (`buildUponDefaultConfig = true`).
- `referenced_by`: every module that runs detekt, via `config.setFrom(...)`.
- **Invariant**: detekt reports **zero** violations across all modules; deviations live in this file,
  never in a baseline.

### Dependency environment selector
- Gradle property `dependency.env` ∈ {default (e.g. `stage`), `public`}; companion `proxy.location`.
- `resolves`: repository set for dependencies **and** plugins (settings `pluginManagement` + root repos).
- **Invariant**: both the default and `public` selections produce a successful resolution of every
  dependency, including the `open-reliquary` CDN artifacts (Konstellation / `khorum.*`); CI uses `public`.

### Integration-tests module
- A registered Gradle module `integration-tests`; produces **no** runnable/publishable artifact.
- `depends_on`: `:engine` (system under test) + JUnit platform + `:core-test`.
- `contributes_to`: the coverage aggregate (over `:engine`).
- **Invariant**: its test task runs green and its coverage is counted at the root.

### Per-feature checklist set
- A `checklists/` directory under each `specs/NNN-*/` feature.
- **Invariant**: the active feature has one (this feature already does, via `/speckit-specify`).

### Agent-context pointer
- The `CLAUDE.md` SPECKIT block pointing at the plan of the feature under active work.
- **Invariant**: points at *this* feature's `plan.md` while it is active, not a stale earlier feature.

### Verification metadata
- `gradle/verification-metadata.xml` with verification + signatures enabled.
- **Invariant**: every added/bumped dependency (Kotlin/KSP, integration-test deps) has an entry;
  verification is never disabled (Principle V).

## Relationships

```
Coverage aggregate ──includes──▶ {engine, dsl, integration-tests}   (excludes core-test)
Shared detekt config ──referenced by──▶ every detekt-running module
dependency.env ──selects──▶ repository set (pluginManagement + root)  ──used by──▶ CI (public)
integration-tests ──depends on──▶ engine ──contributes coverage to──▶ Coverage aggregate
CLAUDE.md pointer ──▶ specs/004-khorum-pattern-alignment/plan.md
Any dep change ──must update──▶ verification-metadata.xml
```

## State / transitions

The Kotlin bump is the only entity with a non-trivial transition:

```
kotlin=2.1.20 ──attempt bump──▶ kotlin=2.3.21 (+matching KSP)
   ├─ build+KSP+tests green ─▶ COMMITTED (2.3.21)
   └─ KSP/Konstellation incompatible ─▶ DEFERRED (stay 2.1.20; record blocker)  [per research.md R1]
```
