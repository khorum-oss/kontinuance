# Tasks: Web UI Refresh — Repo Workspace

**Feature**: 021-repo-workspace | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Input**: [plan.md](./plan.md), [spec.md](./spec.md)

Web-only. No new dependency, no server change.

## Phase 1: Repo workspace (US1)

- [ ] T001 [US1] Rewrite `web/src/lib/components/Login.svelte`: keep the sign-in step (real login); replace
  the repo step with the design's full-screen workspace — header (identity + SIGN OUT), provider filter chips
  with counts, card/list layout toggle, repo grid/list with provider + configured/available badges, footer
  count. Clicking a repo calls `oncomplete`. Colors via `--k-*` (theme-aware).

## Phase 2: Add repo (US2)

- [ ] T002 [US2] In `Login.svelte`: local repo list seeded with defaults, `+ ADD REPO` panel (GitHub / GitLab
  / git URL + branch), add appends to the top and persists to `localStorage` (`knt-repos`); load on mount.

## Phase 3: Tests

- [ ] T003 Update `web/e2e/mock.ts` `enterApp` (sign-in → click a repo, no ENTER button) and the re-enter
  flows in `web/e2e/app.spec.ts`; add a repo-workspace E2E (provider filter + add a repo). Keep all suites
  green.
- [ ] T004 Verify: `pnpm --dir web check` + `test:unit` + `test:e2e`; no new dependency.

## Follow-ups (later PRs, same design)

- Runs progress/commit columns; Pipeline stage-flow + telemetry; Deploy nodes/artifacts/env; Coverage
  drill-down (module → class, dual line/branch bars); Config syntax view + resolved plan — each keeping its
  live API wiring.
