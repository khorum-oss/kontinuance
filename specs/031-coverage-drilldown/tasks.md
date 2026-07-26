# Tasks: Coverage Class-Level Drilldown (real Kover)

**Feature**: 031-coverage-drilldown | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Server

- [X] T001 `KoverCoverageReader`: for each `<class>` in a package, read its LINE/BRANCH counters into the
  module's class list (`ClassCov`); display name = path after `kontinuance/<module>/`, dot-joined.
- [X] T002 Emit `classes` per module (name/linePct/branchPct/missed), worst-covered first (most missed);
  module totals stay package-derived; the class count is unchanged.
- [X] T003 `KoverCoverageReaderTest`: fixture classes carry their own counters; assert per-class display
  name + coverage, and worst-first ordering.

## Web

- [X] T004 `types.ts`: `CoverageClass` + optional `CoverageModule.classes`.
- [X] T005 `Coverage.svelte`: on drilldown render the selected module's real class rows (line/branch bar +
  %, missed); an honest empty state when a module has no class data.
- [X] T006 `e2e/mock.ts`: add `classes` to a module (and leave one without). `app.spec.ts`: assert real
  class rows + the empty state.

## Docs

- [X] T007 `docs/roadmap.md`: the Coverage class drilldown is real (031) — last fake-data gap closed.

## Verification

- [X] T008 `:server:test :server:detekt -Pdependency.env=public` green; web `svelte-check`, Vitest,
  Playwright green.
