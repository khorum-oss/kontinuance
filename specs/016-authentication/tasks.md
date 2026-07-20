# Tasks: Server Authentication & Session

**Feature**: 016-authentication | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Input**: [plan.md](./plan.md), [spec.md](./spec.md), [contracts/auth-api.md](./contracts/auth-api.md),
[data-model.md](./data-model.md), [research.md](./research.md)

All work is in the `:server` module. **No new dependency** (WebFlux + kotlinx-serialization + coroutines +
JDK only); `gradle/verification-metadata.xml` is not touched. Package:
`org.khorum.oss.kontinuance.server.auth`.

`[P]` = parallelizable (distinct file, no dependency on an incomplete task).

## Phase 1: Setup

- [ ] T001 Create the package directories `server/src/main/kotlin/org/khorum/oss/kontinuance/server/auth/`
  and `server/src/test/kotlin/org/khorum/oss/kontinuance/server/auth/`.

## Phase 2: Foundational (blocking — credentials + sessions underpin every story)

- [ ] T002 [P] Implement `AuthCredentials` (@Component) in
  `server/src/main/kotlin/org/khorum/oss/kontinuance/server/auth/AuthCredentials.kt`: bind
  `kontinuance.auth.username` / `kontinuance.auth.password` (nullable, `@Value` defaults null); `enabled` =
  both non-blank; `matches(user, pass)` uses `MessageDigest.isEqual` on UTF-8 bytes for **both** fields and
  combines with a non-short-circuiting `and`; emit a single WARN at startup (`@PostConstruct` or init) when
  not enabled. Never log the password.
- [ ] T003 [P] Implement `SessionStore` (@Component) in
  `server/src/main/kotlin/org/khorum/oss/kontinuance/server/auth/SessionStore.kt`: `ConcurrentHashMap<String,
  String>` token→username; `issue(username): String` (32-byte `SecureRandom`, base64url no-padding token);
  `usernameFor(token): String?`; `revoke(token)`; `const val COOKIE = "KSESSION"`; extract the token byte
  length to a named constant (avoid detekt `MagicNumber`).
- [ ] T004 [P] Unit test `AuthCredentialsTest` in
  `server/src/test/kotlin/org/khorum/oss/kontinuance/server/auth/AuthCredentialsTest.kt`: `enabled` true only
  when both set (both / only-user / only-pass / neither); `matches` accepts the exact pair and rejects wrong
  user, wrong pass, and any input when disabled.

**Checkpoint**: credentials + sessions exist and are unit-verified.

## Phase 3: User Story 1 — Enforce auth when configured (P1) 🎯 MVP

**Goal**: with credentials set, protected endpoints reject requests lacking a valid session.
**Independent test**: creds configured → `GET /api/runs` no cookie = 401; `GET /api/health` = 200.

- [ ] T005 [US1] Implement `AuthWebFilter` (`WebFilter`, `@Component`, `@Order(HIGHEST_PRECEDENCE)`) in
  `server/src/main/kotlin/org/khorum/oss/kontinuance/server/auth/AuthWebFilter.kt`: if
  `!credentials.enabled` → `chain.filter(exchange)` (pass-through); else allow public prefixes
  (`/api/auth/`, exact `/api/health`, `/actuator/`) and, for any other path, require a `KSESSION` cookie
  whose token resolves in `SessionStore`; on failure write `401` + `{"error":"authentication required"}`
  JSON directly to the response. Covers `/api/**`, `/api/runs/stream`, and `/ws/**` uniformly.
- [ ] T006 [US1] Verify the filter guards the SSE and WebSocket paths (they route through the same filter);
  no code beyond T005 if the prefix logic already covers non-public paths — add a comment noting the SSE/WS
  coverage.

**Checkpoint**: enforcement works; a signed-in path still needs US3's login to obtain a cookie (tested
together in `AuthIT`, Phase 5).

## Phase 4: User Story 2 — Open mode + warning when unconfigured (P1)

**Goal**: with no credentials, everything works and a startup warning is logged.
**Independent test**: no creds → `GET /api/runs` = 200; `GET /api/auth/me` → `authRequired:false`.

- [ ] T007 [US2] Add the `kontinuance.auth` block to `server/src/main/resources/application.yml` with empty
  `username`/`password` defaults (open mode) and a comment pointing at `KONTINUANCE_AUTH_USERNAME` /
  `KONTINUANCE_AUTH_PASSWORD`.
- [ ] T008 [P] [US2] Integration test `AuthOpenModeIT` in
  `server/src/test/kotlin/org/khorum/oss/kontinuance/server/auth/AuthOpenModeIT.kt`
  (`@SpringBootTest(RANDOM_PORT)`, no auth properties): `GET /api/runs` = 200; `GET /api/auth/me` returns
  `authRequired:false`. (The startup WARN is exercised by this suite booting in open mode.)

**Checkpoint**: the default/dev/loopback deployment and the existing test suite are provably unaffected.

## Phase 5: User Story 3 — Login, who-am-I, logout (P2)

**Goal**: sign in (set cookie), report identity, sign out (invalidate).
**Independent test**: login correct = 200 + Set-Cookie; me with cookie = 200 username; logout → old cookie
rejected.

- [ ] T009 [US3] Implement `AuthController` (suspend `@RestController`) in
  `server/src/main/kotlin/org/khorum/oss/kontinuance/server/auth/AuthController.kt`:
  - `POST /api/auth/login` — `@RequestBody body: String` parsed with `kotlinx.serialization.json.Json`;
    enforced+correct → `SessionStore.issue`, set `ResponseCookie` `KSESSION` (HttpOnly, SameSite=Lax,
    Path=/) on `exchange.response`, 200 body per contract; enforced+wrong → 401; open → 200
    `authRequired:false`; malformed → 400. Constant-time via `AuthCredentials.matches`.
  - `GET /api/auth/me` — read `KSESSION`; enforced+valid → 200 with username; enforced+invalid → 401; open →
    200 `authRequired:false`.
  - `POST /api/auth/logout` — revoke token if present, expire the cookie (`Max-Age=0`), always 200.
  - Responses as `ResponseEntity<ByteArray>` JSON (kotlinx `buildJsonObject`), matching `RunController`.
- [ ] T010 [P] [US3] Add auth-response JSON helpers where useful (e.g. extend `JsonView` or a small local
  builder) in `server/src/main/kotlin/org/khorum/oss/kontinuance/server/`, keeping the `{authenticated,
  authRequired,username?,error?}` shape from the contract; avoid `TooManyFunctions` on `JsonView`.

**Checkpoint**: full login/me/logout cycle is functional.

## Phase 6: Polish & cross-cutting

- [ ] T011 [US1] [US3] Integration test `AuthIT` in
  `server/src/test/kotlin/org/khorum/oss/kontinuance/server/auth/AuthIT.kt`
  (`@SpringBootTest(RANDOM_PORT)`, `properties = ["kontinuance.auth.username=operator",
  "kontinuance.auth.password=s3cret"]`, `WebTestClient`): `GET /api/runs` no cookie = 401; `GET /api/health`
  = 200; `POST /api/auth/login` wrong = 401; correct = 200 + `Set-Cookie: KSESSION`; `GET /api/runs` with the
  cookie = 200; `GET /api/auth/me` with cookie = 200 (`username`), without = 401; `POST /api/auth/logout` →
  old cookie then `GET /api/runs` = 401.
- [ ] T012 Update docs: `docs/getting-started.md` (Using-the-UI sign-in note + the **Auth & session**
  limitation) and `docs/running.md` (**Security** section) to describe real, opt-in authentication
  (configure `KONTINUANCE_AUTH_USERNAME`/`PASSWORD`; open+warn when unset). Keep the sidebar-name and
  EXIT→project-view items listed as the remaining **web** follow-ups.
- [ ] T013 Run `/opt/gradle/bin/gradle :server:test :server:detekt -Pdependency.env=public`; fix any detekt
  finding (MagicNumber / TooManyFunctions / naming) in code, not by weakening the gate. Confirm `git diff
  --stat gradle/verification-metadata.xml` is empty (no dependency change — SC-006).

## Dependencies & order

- Phase 1 → Phase 2 (foundational) → US1 (Phase 3) → US2 (Phase 4) → US3 (Phase 5) → Polish (Phase 6).
- US1's filter needs US3's login cookie to demonstrate a *successful* protected call, so the end-to-end
  enforced flow is asserted in `AuthIT` (T011) once US3 lands. US2 is independently testable after T007.
- Parallel within a phase: T002/T003/T004 `[P]`; T008 `[P]`; T010 `[P]`.

## MVP scope

**US1 + US2** (Phases 2–4) is the MVP: the API is actually gated when configured and provably unchanged when
not. US3 (login/me/logout) makes the gate usable from a browser and is required for the web follow-up.
