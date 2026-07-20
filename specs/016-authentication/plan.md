# Implementation Plan: Server Authentication & Session

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-authentication/spec.md`

## Summary

Add a real authentication gate to the `:server` module. Operator credentials (`kontinuance.auth.username` /
`kontinuance.auth.password`) toggle enforcement: when **both** are set, a reactive `WebFilter` requires a
valid session cookie on every non-public path; when unset, the filter passes everything through and the
server logs a startup warning (so loopback/dev and the existing `@SpringBootTest` suite keep working). A
session is established at `POST /api/auth/login` (HttpOnly `KSESSION` cookie), inspected at
`GET /api/auth/me`, and destroyed at `POST /api/auth/logout`. Built entirely on the existing WebFlux +
kotlinx-serialization + coroutines + JDK stack — **no new dependency**, so verification metadata is
untouched (Principle V). Web UI wiring is a follow-up.

## Technical Context

**Language/Version**: Kotlin 2.3.21 / JDK 21 (`:server` module).

**Primary Dependencies**: none new. Uses `spring-boot-starter-webflux` (`WebFilter`, `ServerWebExchange`,
`ResponseCookie`), `kotlinx-serialization-json` (parse the login body, build responses), `kotlinx-coroutines`
(suspend handlers), and the JDK `java.security` (`SecureRandom`, `MessageDigest.isEqual`). **No Spring
Security** (it is not in the catalog and would widen the verification trust set).

**Storage**: sessions in an in-memory `ConcurrentHashMap<token, username>` — no persistence, single-instance
(consistent with the existing durability model).

**Testing**: `@SpringBootTest(RANDOM_PORT)` + `WebTestClient` real HTTP round-trips (`AuthIT` enforced,
`AuthOpenModeIT` open) + a `AuthCredentialsTest` unit test for the constant-time compare and the enabled
toggle.

**Target Platform**: the Spring Boot server process (same as 008).

**Constraints**: no new dependency; `gradle/verification-metadata.xml` unchanged; detekt stays green
(watch `MagicNumber` on the token byte length; keep controller/filter function counts under the
`TooManyFunctions` threshold). Sandbox Gradle is 8.14.3; CI 9.5.1 is authoritative for the resolved graph.

**Scale/Scope**: ~4 new source files + `application.yml` edit in `:server`, ~3 test files. No change to
`engine`/`persistence`/`web`.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive. The `/api` read/stream/trigger contract
  is unchanged in **open mode** (the default), so no existing consumer breaks. Enforcement is opt-in via
  configuration; the new `/api/auth/**` endpoints are additive surface.
- **II. Test-First & Integration-Verified**: PASS — the auth boundary is exercised over a **real HTTP
  round-trip** (`@SpringBootTest(RANDOM_PORT)` + `WebTestClient`) in both enforced and open modes, plus a
  unit test for the credential compare. This is the real request boundary (no mocking the filter away).
- **III. Quality Gates**: PASS — detekt + Kover run on `:server`; the new code is small and fully
  test-exercised.
- **IV. Correct, Covered Code Generation**: N/A — no KSP/codegen here.
- **V. Supply-Chain Integrity**: PASS — **no new dependency**; `verification-metadata.xml` is not touched;
  no secrets committed (credentials come from the environment).

No violations → Complexity Tracking empty.

## Project Structure

```text
server/src/main/kotlin/org/khorum/oss/kontinuance/server/auth/
├── AuthCredentials.kt        # NEW — @Component; reads kontinuance.auth.*; enabled iff both set;
│                             #        constant-time matches(user,pass); startup WARN when open
├── SessionStore.kt           # NEW — @Component; ConcurrentHashMap<token,username>; SecureRandom
│                             #        base64url token; issue/lookup/revoke; COOKIE = "KSESSION"
├── AuthWebFilter.kt          # NEW — WebFilter @Order(HIGHEST_PRECEDENCE); pass-through when open;
│                             #        else allow public paths, require KSESSION else 401 JSON
└── AuthController.kt         # NEW — suspend @RestController: POST /api/auth/login, GET /api/auth/me,
                              #        POST /api/auth/logout; raw ByteArray JSON like RunController

server/src/main/resources/application.yml   # EDIT — add empty kontinuance.auth block (open by default)

server/src/test/kotlin/org/khorum/oss/kontinuance/server/auth/
├── AuthIT.kt                 # NEW — @SpringBootTest(RANDOM_PORT), creds via properties: 401 without
│                             #        cookie, health public, login wrong=401, login right=200+Set-Cookie
│                             #        → protected call with cookie=200, me with/without cookie, logout
├── AuthOpenModeIT.kt         # NEW — @SpringBootTest(RANDOM_PORT), no creds: protected call=200,
│                             #        me → authRequired=false
└── AuthCredentialsTest.kt    # NEW — unit: constant-time match/reject + enabled toggle (both/one/none)
```

**Structure Decision**: A new `server/.../auth/` package holding four small components behind one reactive
`WebFilter`. The filter is the single choke point (all requests pass through it before routing, including
the SSE and WebSocket upgrades), so protection is uniform without touching any existing controller. Open
mode is the default, keeping every current test and the loopback deployment working unchanged.

## Complexity Tracking

> No Constitution Check violations — no entries.
