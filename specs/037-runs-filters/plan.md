# Implementation Plan: Runs List Filters & Search

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/037-runs-filters/spec.md`

## Summary

Add status/trigger filters and a search box to the runs list, entirely client-side. A pure `filterRuns(records,
{query, status, trigger})` helper in `present.ts` narrows the already-held, live-updated run set (`byId` in the
runs route) before it is projected to views; the `Runs` screen gains the controls in its toolbar (search input
+ two selects) and a "showing N of M" count with a distinct "no runs match" state. No server change — the runs
stream and records are untouched.

## Technical Context

**Language/Version**: Svelte 5 + TypeScript (`web`); no backend change.

**Primary Dependencies**: none new — reuses `normalizeStatus` (theme tokens) for status matching.

**Storage**: none — operates on the in-memory `byId` run set.

**Testing**: Vitest unit tests for `filterRuns` (status / trigger / query / compose / blank-query / missing
fields); Playwright e2e over `sampleRuns` (status filter narrows, search narrows, "no runs match", count).
`svelte-check`.

**Constraints**: client-side only; filters compose (AND); blank search matches all; status matched by
canonical status; clearing restores instantly; underlying set never mutated.

**Scale/Scope**: `web/src/lib/api/present.ts` (+`filterRuns` + `RunFilter`), `web/src/lib/screens/Runs.svelte`
(toolbar controls + count + no-match state), `web/src/routes/+page.svelte` (filter state → filtered render),
`web/src/lib/api/present.test.ts` + `web/e2e/app.spec.ts`; docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — no API change; a pure view-layer projection over
  existing run records.
- **II. Test-First & Integration-Verified**: PASS — the filter predicate is unit-tested and the UI is
  E2E-tested (filter + search + no-match + count).
- **III. Quality Gates**: PASS — svelte-check + Vitest + Playwright on `web` (no server module touched).
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency.

No violations → Complexity Tracking empty.

## Project Structure

```text
web/src/lib/api/present.ts            # EDIT — RunFilter + filterRuns(records, criteria) (pure)
web/src/lib/screens/Runs.svelte       # EDIT — toolbar: search + status/trigger selects; count; no-match state
web/src/routes/+page.svelte           # EDIT — filter $state; apply filterRuns in render(); pass controls
web/src/lib/api/present.test.ts       # EDIT — filterRuns unit tests
web/e2e/app.spec.ts                   # EDIT — filter + search + no-match e2e
docs/getting-started.md               # EDIT — runs list filters/search (037)
```

**Structure Decision**: Keep the container/presentational split — the route (`+page.svelte`) owns the filter
state and applies the pure `filterRuns` over `mergeNewestFirst(byId.values())` before `toRunView`, so `byId`
stays the untouched live set (clearing a filter is instant and streamed runs keep landing). The predicate lives
in `present.ts` as a pure function so it is unit-testable without a component. The `Runs` screen stays
presentational: it renders the controls and emits change callbacks, and shows the visible/total count + the
"no runs match" state.

## Complexity Tracking

> No Constitution Check violations — no entries.
