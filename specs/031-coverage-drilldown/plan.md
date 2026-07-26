# Implementation Plan: Coverage Class-Level Drilldown (real Kover)

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/031-coverage-drilldown/spec.md`

## Summary

Extend `KoverCoverageReader` to fold each `<package>`'s `<class>` elements (with their own LINE/BRANCH
counters) into the module they belong to, emitting a per-module `classes` array (name / linePct / branchPct /
missed, worst-covered first) on `/api/coverage`. The class display name is the path after the module,
dot-joined (`model.Step`). The web Coverage screen renders the selected module's real classes on drilldown
(replacing the placeholder), with an honest empty state when a module has no class data. Additive to the
response shape; module totals stay package-derived; no new dependency.

## Technical Context

**Language/Version**: Kotlin/JDK 21 + Spring WebFlux (`:server`) + Svelte 5 (`web`).

**Primary Dependencies**: none new — the JDK DOM parser (XXE-safe) already reads the report.

**Storage**: none.

**Testing**: `KoverCoverageReaderTest` gains a per-class assertion (display name + coverage) and a worst-first
ordering check, against a fixture whose classes carry their own counters. Web: `svelte-check`, Vitest, and
the Playwright coverage drilldown E2E updated to assert real class rows (+ the honest empty state).

**Constraints**: additive response shape (optional `classes` per module); module coverage unchanged
(package-derived); honest empty state; no new dependency.

**Scale/Scope**: `server/.../coverage/KoverCoverageReader.kt` (per-class parse + `classes` array);
`KoverCoverageReaderTest.kt`; web `types.ts` (`CoverageClass` + `CoverageModule.classes`),
`screens/Coverage.svelte` (drilldown table), `e2e/mock.ts` + `app.spec.ts`; docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive optional `classes` array on the existing
  `/api/coverage` shape; totals/module rows unchanged.
- **II. Test-First & Integration-Verified**: PASS — the parser is unit-tested (per-class coverage + ordering)
  and the drilldown is E2E-tested (real rows + empty state).
- **III. Quality Gates**: PASS — detekt/Kover on `:server`; svelte-check + Vitest + Playwright on `web`.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency.

No violations → Complexity Tracking empty.

## Project Structure

```text
server/.../coverage/KoverCoverageReader.kt        # EDIT — collect per-class coverage; emit module.classes
server/.../coverage/KoverCoverageReaderTest.kt (test) # EDIT — per-class assertions + worst-first ordering
web/src/lib/api/types.ts                            # EDIT — CoverageClass + CoverageModule.classes?
web/src/lib/screens/Coverage.svelte                 # EDIT — drilldown renders real class rows / empty state
web/e2e/mock.ts + app.spec.ts                       # EDIT — module classes fixture + drilldown E2E
docs/roadmap.md                                     # EDIT — coverage drilldown is real (031)
```

**Structure Decision**: Keep module coverage exactly as it is (package-derived) and add the class rows as an
additive breakdown, so the change is purely additive and the existing module assertions and UI stay valid.
Fold classes into their module in the reader (one pass over the same XML), so there is no second source.

## Complexity Tracking

> No Constitution Check violations — no entries.
