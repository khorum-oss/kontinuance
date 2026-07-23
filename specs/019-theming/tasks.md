# Tasks: Light/Dark Theme & Brightness

**Feature**: 019-theming | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Input**: [plan.md](./plan.md), [spec.md](./spec.md)

Web-only. No new dependency. `[P]` = parallelizable.

## Phase 1: Foundational — theme-drive every color

- [ ] T001 [P] `web/src/lib/theme/tokens.ts`: change the `color` map values from hex to `var(--k-*)`
  references (keep `toolColor` hex). The helpers (`statusColor`/`coverageColor`/`toolAccent`) are unchanged
  and `tokens.test.ts` stays green.
- [ ] T002 [P] `web/src/lib/theme/preferences.ts` (NEW): `MODE_KEY`/`BRIGHTNESS_KEY`, `MIN`/`MAX`/`DEFAULT`
  brightness, `clampBrightness(v)`, `resolveMode(stored, systemPrefersLight)`. Pure, DOM-free.
- [ ] T003 [P] `web/src/lib/theme/preferences.test.ts` (NEW): clamp bounds/NaN; resolveMode (stored wins →
  else OS → else dark).

## Phase 2: User Story 1 — light/dark (P1) 🎯 MVP

- [ ] T004 [US1] `web/src/app.css`: add `:root { color-scheme: dark }` + a `:root[data-theme='light']`
  block overriding every `--k-*` (canvas/surface/border/text/muted/accent) for a legible light palette and
  `color-scheme: light`; make the `body` gradient + scrollbar theme-aware.
- [ ] T005 [US1] `web/src/routes/+layout.svelte`: `mode` state; `onMount` init via
  `resolveMode(localStorage, matchMedia('(prefers-color-scheme: light)'))`; an `$effect` sets
  `document.documentElement.dataset.theme = mode`; `toggleTheme()` flips + persists to `localStorage`.
- [ ] T006 [US1] `web/src/lib/components/ThemeControls.svelte` (NEW): a light/dark toggle button (emits
  `ontoggle`), styled with the theme vars; `web/src/lib/components/Topbar.svelte` renders it and forwards
  `mode`/`ontoggle`; layout passes them.

**Checkpoint**: the whole UI (incl. status colors + login) switches light↔dark and persists.

## Phase 3: User Story 2 — brightness (P2)

- [ ] T007 [US2] `app.css`: `body { filter: brightness(var(--k-brightness, 1)); }` (default var on `:root`).
- [ ] T008 [US2] `+layout.svelte`: `brightness` state; `onMount` reads/clamps stored value; `$effect` sets
  `--k-brightness` on `document.documentElement`; `setBrightness(v)` clamps + persists. `ThemeControls` gains
  a bounded range slider (emits `onbrightness`); Topbar/layout forward it.

## Phase 4: Polish & verification

- [ ] T009 [P] `web/src/lib/components/ThemeControls.stories.svelte` (NEW): light + dark variants.
- [ ] T010 E2E `web/e2e/app.spec.ts`: enter the app → default `data-theme` present; toggle flips
  light↔dark; move the slider → `--k-brightness` changes; reload keeps both. Keep existing suites green.
- [ ] T011 Docs: `docs/getting-started.md` — drop "Dark theme only" from limitations (now light/dark +
  brightness). Verify: `pnpm --dir web check` + `test:unit` + `test:e2e`; no new dependency.

## Dependencies & MVP

- T001/T002/T003 first. US1 (T004–T006) is the MVP (light/dark + persistence). US2 (T007/T008) adds
  brightness. T001 makes inline-styled status colors adapt (FR-006) and is required before the light block
  is meaningful.
