# Implementation Plan: Web UI Refresh — Repo Workspace

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/021-repo-workspace/spec.md`

## Summary

Rewrite `Login.svelte`'s repo step into the imported design's full-screen **repo workspace** while keeping
the sign-in step (real 016/017 login) unchanged. The workspace has a header (identity + SIGN OUT), a toolbar
(**+ ADD REPO**, provider filter chips with counts, a card/list layout toggle), a collapsible add-repo panel
(provider + URL/branch), a repo grid/list with provider + configured/available badges, and a footer count.
Clicking a repo enters mission control (`oncomplete`). The repo list is local state seeded with defaults and
persisted to `localStorage` (`knt-repos`) — a server-backed repo registry is a later feature. Colors go
through the theme's `--k-*` variables (so it works in light + dark). No new dependency, no server change.

## Technical Context

**Language/Version**: Svelte 5 (runes) / TypeScript, SvelteKit (`ssr=false`).

**Primary Dependencies**: none new — existing `api.login` (016), `localStorage`, CSS variables (019).

**Storage**: `localStorage` `knt-repos` (device-local repo list).

**Testing**: `svelte-check`; Playwright E2E — the redesigned entry flow (sign-in → click a repo) plus a new
test for provider filtering + adding a repo; existing suites updated for the new flow (no ENTER button).

**Constraints**: keep the real auth flow (sign-in / EXIT → workspace / SIGN OUT); theme-aware; no new
dependency.

**Scale/Scope**: `Login.svelte` (rewrite), `web/e2e/mock.ts` (`enterApp`), `web/e2e/app.spec.ts` (flow +
new test). No server/other-screen change in this slice.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — no API/DSL change; presentational + local state.
- **II. Test-First & Integration-Verified**: PASS — the flow is exercised end-to-end in Playwright (sign-in,
  filter, add, enter, EXIT, open mode).
- **III. Quality Gates**: PASS — svelte-check + Vitest + Playwright via the web CI job.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency; JVM verification untouched.

No violations → Complexity Tracking empty.

## Project Structure

```text
web/src/lib/components/Login.svelte   # REWRITE — auth step unchanged; repo step → full-screen repo workspace
web/e2e/mock.ts                       # EDIT — enterApp: sign-in → click a repo (no ENTER button)
web/e2e/app.spec.ts                   # EDIT — update re-enter flows; add a repo-workspace test
```

**Structure Decision**: Keep it in `Login.svelte` (the layout already owns the auth/session state and passes
`requireSignIn`/`operator`/`onauthenticated`/`oncomplete`/`onsignout`). The workspace is local UI over a
`localStorage`-persisted repo list — faithful to the design without needing a backend, and not touching the
API-wired screens.

## Complexity Tracking

> No Constitution Check violations — no entries.
