# Implementation Plan: Light/Dark Theme & Brightness

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-theming/spec.md`

## Summary

Web-only. The palette is already CSS custom properties (`--k-*`) in `app.css`, but a few status/accent colors
are applied via `tokens.ts` inline styles (raw hex), so a light theme needs those routed through the
variables too. Change the `tokens.ts` `color` map from hex to `var(--k-*)` references (the helpers already
return `color.X`, so `tokens.test.ts` keeps passing) — now **every** color flows through CSS variables. Add a
`:root[data-theme='light']` override block (+ `color-scheme`) and a `--k-brightness` filter to `app.css`. A
tiny pure preferences module (clamp + resolve-initial-mode) holds the logic; the root layout owns the
`{mode, brightness}` state, initialises from `localStorage` else the OS preference, applies it to
`document.documentElement` (a `data-theme` attribute + a `--k-brightness` custom property) and persists it.
A `ThemeControls` component (toggle + brightness slider) sits in the top bar. No new dependency, no server
change, no auth.

## Technical Context

**Language/Version**: TypeScript + Svelte 5 (runes), SvelteKit (`ssr=false`).

**Primary Dependencies**: none new — CSS variables, `localStorage`, `matchMedia`.

**Storage**: `localStorage` (`knt-theme`, `knt-brightness`) — device-local UI preference.

**Testing**: Vitest unit for the pure preferences helpers (clamp, initial-mode resolution); Playwright E2E
(toggle flips `data-theme`; the slider changes `--k-brightness`; both persist across reload). `tokens.test.ts`
stays green (helpers still return `color.X`).

**Target Platform**: modern browser.

**Constraints**: no new dependency; brightness bounded to a sensible range; the theme must apply to every
view (login + app), and inline-styled status colors must adapt (FR-006).

**Scale/Scope**: `app.css` + `tokens.ts` + one new component + one new helper module + the layout/topbar;
plus tests/stories. No `.kt` change.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — purely presentational; no API/DSL change.
- **II. Test-First & Integration-Verified**: PASS — pure logic unit-tested; the runtime behavior (toggle,
  slider, persistence) exercised end-to-end in Playwright.
- **III. Quality Gates**: PASS — `svelte-check` + Vitest + Playwright via the web CI job.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency; JVM verification untouched.

No violations → Complexity Tracking empty.

## Project Structure

```text
web/src/app.css                            # EDIT — :root[data-theme='light'] overrides, color-scheme,
                                           #        --k-brightness filter, theme-aware body gradient/scrollbar
web/src/lib/theme/tokens.ts                # EDIT — color map hex → var(--k-*) (helpers unchanged; test green)
web/src/lib/theme/preferences.ts           # NEW — clampBrightness, resolveMode, MIN/MAX/DEFAULT consts
web/src/lib/theme/preferences.test.ts      # NEW — unit tests for the pure helpers
web/src/lib/components/ThemeControls.svelte # NEW — light/dark toggle + brightness slider (emits changes)
web/src/lib/components/ThemeControls.stories.svelte # NEW
web/src/lib/components/Topbar.svelte        # EDIT — render ThemeControls, pass mode/brightness + callbacks
web/src/routes/+layout.svelte              # EDIT — theme state; init from storage/OS; apply to <html>;
                                           #        persist; pass to Topbar
web/e2e/app.spec.ts                        # EDIT — theme toggle + brightness + persistence E2E
```

**Structure Decision**: Route *all* color through the existing `--k-*` variables (the one real refactor:
`tokens.ts` hex → var refs), so a single `data-theme` attribute re-themes the whole app — chrome and
inline-styled status colors alike. Brightness is a bounded global `filter: brightness()` driven by a
`--k-brightness` custom property, independent of the theme.

## Complexity Tracking

> No Constitution Check violations — no entries.
