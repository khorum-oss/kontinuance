# Tasks: Web Sign-In & Session Wiring

**Feature**: 017-web-auth | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Input**: [plan.md](./plan.md), [spec.md](./spec.md), [contracts/web-auth-flow.md](./contracts/web-auth-flow.md)

All work is in `web/`. No new dependency. `[P]` = parallelizable (distinct file).

## Phase 1: Foundational — API client (blocking)

- [ ] T001 [P] Add `Session` (`{authenticated, authRequired, username?}`) to `web/src/lib/api/types.ts`.
- [ ] T002 Add `me()`, `login(username, password)`, `logout()` to `web/src/lib/api/client.ts`: `me()` GETs
  `/api/auth/me` and maps a `401` to `{authenticated:false, authRequired:true}` (from the body) rather than
  throwing; `login()` POSTs JSON and throws `ApiError` with the server `error` on `401`; `logout()` POSTs
  best-effort.
- [ ] T003 [P] Unit-test the three methods in `web/src/lib/api/client.test.ts` (me: 200 + 401 mapping;
  login: success + 401 error message; logout: POSTs and resolves).

## Phase 2: User Story 1 + 2 — sign-in / open-mode gating (P1) 🎯 MVP

**Goal**: the UI honors the server's real auth state.

- [ ] T004 [US1] [US2] Rework `web/src/routes/+layout.svelte` into a view state machine
  (`loading → signin → project → app`): probe `api.me()` on mount, choose `signin` (authRequired &&
  !authenticated) vs `project`; render `<Login>` for signin/project and the app shell (`Sidebar` + route
  `children`) only in `app`; on `me()` failure fall back to `project`.
- [ ] T005 [US1] [US2] Update `web/src/lib/components/Login.svelte`: SIGN IN calls `api.login()` — show an
  error and stay on failure, `onauthenticated(username)` + advance to the repo step on success; add
  `requireSignIn` (start at auth vs repo step); in open mode hide the "signed in as" identity row.

## Phase 3: User Story 3 — identity, EXIT, sign-out (P2)

**Goal**: sidebar shows the operator; EXIT → project (session intact); sign-out → sign-in.

- [ ] T006 [US3] `web/src/lib/components/Sidebar.svelte`: take `operator` from a prop (default neutral
  `operator`), remove the hard-coded `mkuraja`; keep EXIT → `onexit`.
- [ ] T007 [US3] In `+layout.svelte`, pass the signed-in username to `Sidebar`; wire `onexit` → `project`
  (session intact) and a `onsignout` (from the project view) → `api.logout()` → `signin` (only when
  authRequired). Add the sign-out affordance to `Login.svelte`'s project step (repurpose SWITCH).

## Phase 4: Polish & verification

- [ ] T008 [P] Update the stories so they compile with the new props:
  `web/src/lib/components/Login.stories.svelte` (a signed-in/project variant) and
  `Sidebar.stories.svelte` (pass an `operator` arg).
- [ ] T009 Update the E2E harness: add `mockAuth(page)` (routes `/api/auth/me`, `/api/auth/login`,
  `/api/auth/logout`) and adjust `enterApp()` in `web/e2e/mock.ts`; add a `beforeEach(mockAuth)` and
  auth-flow tests to `web/e2e/app.spec.ts` (wrong creds → error, EXIT → project view, sidebar shows the
  signed-in name). Keep the existing suites green.
- [ ] T010 Update docs (`docs/getting-started.md` "Auth & session" + the Using-the-UI sign-in note) to say
  the web login is now wired; drop the "not wired" caveat and keep any genuinely remaining items.
- [ ] T011 Run `pnpm --dir web check` and `pnpm --dir web test:unit` (and `test:e2e` if the browser is
  available); fix issues. Confirm no new dependency was added (`web/package.json` unchanged).

## Dependencies & MVP

- T001/T002 block everything; T004 depends on T002. US3 (T006/T007) builds on the state machine from US1/US2.
- **MVP = Phase 1 + Phase 2** (US1+US2): the UI honors the real gate and open mode. US3 is the identity/EXIT
  polish. Parallel: T001/T003, T008.
