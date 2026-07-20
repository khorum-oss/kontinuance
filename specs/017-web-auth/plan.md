# Implementation Plan: Web Sign-In & Session Wiring

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-web-auth/spec.md`

## Summary

Wire the SvelteKit SPA (`web/`) to the 016 server auth endpoints. Add `me()` / `login()` / `logout()` to the
API client, then drive the shell from the server's session state: the root layout probes `GET /api/auth/me`
on load and shows a **view state machine** — `loading → signin → project → app` — instead of a boolean
overlay. Sign-in POSTs to `/api/auth/login` (error on 401); open mode (`authRequired:false`) skips the
sign-in step; the app shell (and therefore its data fetches) mounts only once the operator enters, so an
enforced deployment fires no unauthenticated calls. The sidebar shows the signed-in username; **EXIT**
returns to the project view with the session intact; a **sign-out** action ends the session and returns to
sign-in. Same-origin, so the HttpOnly `KSESSION` cookie flows automatically — no new dependency.

## Technical Context

**Language/Version**: TypeScript + Svelte 5 (runes) on SvelteKit (`adapter-static`, `ssr=false`).

**Primary Dependencies**: none new — the browser `fetch` and the existing client/live modules only.

**Storage**: none in the browser (the session lives in the server's store; the client holds only the
derived session *view* in component state).

**Testing**: Vitest unit tests for the new client methods (`web/src/lib/api/client.test.ts`); Playwright E2E
for the flow (`web/e2e/`), with `/api/auth/*` added to the route mocks.

**Target Platform**: modern browser; same-origin with the server (reverse proxy in prod, Vite proxy in dev).

**Constraints**: no new dependency; the token cookie is HttpOnly (never read by JS); `me()` must treat `401`
as a valid "not authenticated" answer, not a transport error.

**Scale/Scope**: ~2 API files, 3 components/routes, 1 unit test file, E2E mock + spec. `web` only.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — consumes the existing 016 `/api/auth/**` contract;
  no server change. Additive UI behavior.
- **II. Test-First & Integration-Verified**: PASS — client methods unit-tested against a mocked `fetch`;
  the flow exercised end-to-end in Playwright over the real route boundary (auth endpoints mocked at the
  wire, like the other API calls).
- **III. Quality Gates**: PASS — `pnpm --dir web check` (svelte-check) + Vitest run; the web test job in CI
  covers it.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency; JVM verification metadata untouched (web change).

No violations → Complexity Tracking empty.

## Project Structure

```text
web/src/lib/api/
├── types.ts            # EDIT — add the `Session` type {authenticated, authRequired, username?}
└── client.ts           # EDIT — add me() (tolerates 401), login(user,pass), logout()

web/src/routes/
└── +layout.svelte      # EDIT — view state machine (loading→signin→project→app); probe me() on mount;
                        #        gate the app shell (children) on `app`; wire onexit→project, sign-out

web/src/lib/components/
├── Login.svelte        # EDIT — call login() on SIGN IN (+ error); requireSignIn prop (start at auth vs
│                       #        repo step); onauthenticated(username); onsignout(); hide identity/sign-out
│                       #        in open mode
├── Sidebar.svelte      # EDIT — operator from prop (default neutral 'operator'); EXIT unchanged (onexit)
├── Login.stories.svelte  # EDIT — stories still compile with the new props
└── Sidebar.stories.svelte# EDIT — pass an operator arg

web/src/lib/api/client.test.ts   # EDIT — me()/login()/logout() unit tests
web/e2e/mock.ts                  # EDIT — mockAuth(): /api/auth/me|login|logout; update enterApp()
web/e2e/app.spec.ts              # EDIT — beforeEach mockAuth; auth-flow tests (bad creds, EXIT, name)
```

**Structure Decision**: Keep the session as derived UI state owned by the layout (Svelte 5 runes), fed by a
single `me()` probe — no store library. Gating the app shell on the `app` view (rather than overlaying the
login on a live app) both implements FR-004 and removes the current "app fetches run behind the overlay"
wart.

## Complexity Tracking

> No Constitution Check violations — no entries.
