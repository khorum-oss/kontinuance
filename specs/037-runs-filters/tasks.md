# Tasks: Runs List Filters & Search

**Feature**: 037-runs-filters | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Web

- [ ] T001 `present.ts`: `RunFilter { query, status, trigger }` + pure `filterRuns(records, f)` — status by
  `normalizeStatus`, trigger case-insensitive (`all`/`manual`/`push`/`pull_request`), query a case-insensitive
  substring over id/pipeline/repo/sha; blank query matches all.
- [ ] T002 `Runs.svelte`: toolbar gains a search input (`aria-label` "filter runs") + status and trigger
  `<select>`s (`aria-label`s); a "showing N of M" count; and a distinct "no runs match" state when filters
  exclude everything. New props (`query`, `status`, `trigger`, `total`) + change callbacks.
- [ ] T003 `+page.svelte`: filter `$state` (query/status/trigger); apply `filterRuns` over
  `mergeNewestFirst(byId.values())` in `render()`; re-render on filter change; pass values + `total`
  (`byId.size`) + handlers to `Runs`.
- [ ] T004 `present.test.ts`: `filterRuns` unit tests — status, trigger, query, compose (AND), blank query,
  missing repo/sha.
- [ ] T005 `e2e/app.spec.ts`: a status filter narrows to the matching run; a search narrows by commit; an
  over-filtered search shows "no runs match"; the count reflects the visible/total.

## Docs

- [ ] T006 `docs/getting-started.md`: the runs list can be filtered (status/trigger) and searched (037).

## Verification

- [ ] T007 web `svelte-check`, Vitest, Playwright green.
