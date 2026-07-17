# Tasks: Spring Boot Server (Coroutine API Runtime)

**Input**: Design documents from `/specs/008-spring-boot-server/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, contracts/api-contract.md ✅, quickstart.md ✅

**Tests**: INCLUDED — the feature explicitly requires `@SpringBootTest(RANDOM_PORT)` + `WebTestClient`
real-HTTP round-trip tests (FR-008, SC-005, Constitution II).

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` (008 numbers the `specs/` dir only — no new branch).

**Organization**: Tasks grouped by user story. US1 (contract parity) is the MVP; US2 (non-blocking) and
US3 (verification stays enabled) are additive quality/architecture outcomes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 (setup, foundational, polish carry no story label)
- Paths are repo-relative; module root is `server/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Introduce Spring Boot through the version catalog + module build, keeping verification enabled.

- [X] T001 Add Spring Boot to the version catalog in `gradle/libs.versions.toml`: `springBoot = "4.1.0"` +
  `springDependencyManagement` version; `[plugins]` `spring-boot` (`org.springframework.boot`),
  `spring-dependency-management` (`io.spring.dependency-management`), `kotlin-spring`
  (`org.jetbrains.kotlin.plugin.spring`, `version.ref = "kotlin"`); `[libraries]`
  `spring-boot-starter-webflux`, `spring-boot-starter-actuator`, `spring-boot-starter-test`,
  `reactor-test` (`io.projectreactor:reactor-test`) — modules only, versions from the Boot BOM.
- [X] T002 Rewrite `server/build.gradle.kts`: apply `spring-boot`, `spring-dependency-management`,
  `kotlin-spring` (+ keep `detekt`); `implementation` webflux + actuator + `:persistence` +
  `serialization.json` + `coroutines.core`; `testImplementation` `spring-boot-starter-test`,
  `reactor-test`, `:core-test`; point the `application` `mainClass` at
  `org.khorum.oss.kontinuance.server.KontinuanceApiApplicationKt` and rename the install launcher target;
  drop the `serialization.json`-only comment header for the Spring rationale.
- [X] T003 Verify `gradle/verification-metadata.xml` carries the 13 group trusts from research R2
  (`org.springframework`, `io.micrometer`, `io.projectreactor`, `org.reactivestreams`, `ch.qos.logback`,
  `jakarta`, `tools.jackson`, `org.yaml`, `org.osgi`, `org.bouncycastle`, `org.apache.logging`,
  `biz.aQute.bnd`, `commons-logging`) as `<trusted-artifact group="^…($|([.].*))"/>` regex trusts, and
  that no `verification` element is set to `false` (Principle V).

**Checkpoint**: `:server` declares Spring Boot, verification stays enabled — implementation can begin.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The Spring application shell + configuration that every endpoint needs. BLOCKS all stories.

**⚠️ CRITICAL**: No controller/facade work runs until the app context boots.

- [X] T004 Create `server/src/main/kotlin/org/khorum/oss/kontinuance/server/KontinuanceApiApplication.kt` —
  `@SpringBootApplication` class + `main(args)` calling `runApplication<KontinuanceApiApplication>(*args)`.
- [X] T005 [P] Create `server/src/main/kotlin/org/khorum/oss/kontinuance/server/ServerConfig.kt` —
  `@Configuration` exposing a `RunStore` `@Bean` resolved from the `kontinuance.store` property /
  `KONTINUANCE_STORE` env (default `~/.kontinuance/runs`) as a `FileRunStore`, plus a `RunApi` `@Bean` over it.
- [X] T006 [P] Create `server/src/main/resources/application.yml` — expose actuator `health`
  (`management.endpoints.web.exposure.include: health`), default `kontinuance.store`, and a sensible
  `server.port`/`spring.application.name`.

**Checkpoint**: App context boots with a `RunStore`/`RunApi` bean and actuator health available.

---

## Phase 3: User Story 1 - The same API, now on the platform runtime (Priority: P1) 🎯 MVP

**Goal**: Serve the unchanged `/api` contract from a Spring Boot app (with actuator health).

**Independent Test**: Boot the app over a store with runs; assert health/list(newest-first, bounded)/
detail/404/405 match the 007 contract exactly, and `/actuator/health` reports UP.

### Tests for User Story 1 ⚠️ (write first, expect red)

- [X] T007 [P] [US1] `server/src/test/kotlin/.../server/RunApiContractIT.kt` —
  `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`: seed an in-memory/temp store bean,
  assert `GET /api/health` → 200 `{"status":"ok"}`; `GET /api/runs?limit=2` newest-first + bound;
  `GET /api/runs/{id}` 200 with body / 404 `{"error":"not found"}` for unknown; unknown path → 404;
  `POST /api/health` → 405; `GET /actuator/health` → 200 `"UP"`.

### Implementation for User Story 1

- [X] T008 [US1] Create `server/src/main/kotlin/.../server/RunReadFacade.kt` — `suspend` `health()`,
  `listRuns(limit: Int?)`, `getRun(id)` delegating to the injected `RunApi` inside
  `withContext(Dispatchers.IO)` (the file store is blocking). Returns the existing `ApiResponse`.
- [X] T009 [US1] Create `server/src/main/kotlin/.../server/RunController.kt` — `@RestController` with
  `suspend` handlers: `GET /api/health`, `GET /api/runs` (`@RequestParam limit: Int?`),
  `GET /api/runs/{id}`; map `ApiResponse(status,json)` → `ResponseEntity` with that status,
  `Content-Type: application/json`, and the raw JSON body (bypass Jackson re-serialization to keep the
  byte shape identical to 007).
- [X] T010 [US1] Delete `server/src/main/kotlin/.../server/HttpApiServer.kt` and
  `server/src/main/kotlin/.../server/cli/ApiServerMain.kt` (replaced by the Spring app); delete the now-stale
  `server/src/test/kotlin/.../server/HttpApiServerIT.kt` (superseded by T007).

**Checkpoint**: The Spring Boot service serves the full `/api` contract + actuator health (MVP complete).

---

## Phase 4: User Story 2 - Non-blocking request handling with structured concurrency (Priority: P2)

**Goal**: Handlers suspend; the one blocking store hop is offloaded, keeping the request path non-blocking.

**Independent Test**: Fire concurrent requests and assert each returns correctly; assert the facade crosses
a `withContext(Dispatchers.IO)` boundary (no blocking store call on the event-loop thread).

### Tests for User Story 2 ⚠️

- [X] T011 [P] [US2] Add a concurrency test to `RunApiContractIT.kt` (or a sibling IT): issue N simultaneous
  `WebTestClient` calls and assert all return the correct, non-interfering responses.
- [X] T012 [P] [US2] `server/src/test/kotlin/.../server/RunReadFacadeTest.kt` — a `runTest`/coroutine unit
  test asserting the `suspend` facade returns the same `ApiResponse` as the underlying `RunApi` (the
  offload boundary is exercised without blocking the caller).

### Implementation for User Story 2

- [X] T013 [US2] Confirm the T008 facade offloads via `withContext(Dispatchers.IO)` and the T009 handlers
  are `suspend` calling only the facade (no direct blocking `RunApi`/store call on the request thread);
  adjust if T007's design left any blocking hop on the event loop.

**Checkpoint**: Concurrent requests are served non-blocking; the blocking hop is provably offloaded.

---

## Phase 5: User Story 3 - Verification stays enabled through the dependency change (Priority: P1)

**Goal**: The large Spring dependency addition is trusted via added metadata — verification never disabled.

**Independent Test**: With verification enabled, the graph resolves/builds; confirm the trusts cover it and
no `verification=false` / removed gate exists anywhere.

### Implementation for User Story 3

- [X] T014 [US3] Resolve `:server` with verification enabled and confirm the 13 trusts (T003) cover the full
  Spring/WebFlux/actuator graph on Gradle 8.14.3; record any additional group surfaced by the resolve into
  `verification-metadata.xml` (group trust only — no per-artifact PGP) and note the result in research R2.
- [X] T015 [US3] Guard check: confirm no `verification` element is set to `false` and no
  `--no-verify`/baseline was introduced in `gradle/` or module builds (Principle V); CI on 9.5.1 is the
  authoritative gate (research R5) — note this in the PR/commit body when the work lands.

**Checkpoint**: Verification is enabled and green for the new graph; nothing was disabled or baselined.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T016 [P] Update `docs/roadmap.md` — mark 007's "real Spring Boot" deferral as delivered by 008
  (Spring Boot 4.1.0 WebFlux + actuator, suspend controllers), keeping the streaming/write/auth follow-ups.
- [X] T017 [P] Run `specs/008-spring-boot-server/quickstart.md` validation (build, run, `curl` the endpoints
  + `/actuator/health`) and reconcile any drift.
- [X] T018 Run `./gradlew :server:detekt :server:test` (system Gradle 8.14.3) and clear any detekt/test
  findings on the new files; confirm the module stays under the shared gates (Constitution III).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (P1)**: no deps — start immediately.
- **Foundational (P2)**: depends on Setup — BLOCKS all stories (app context must boot).
- **US1 (P3)**: depends on Foundational — the MVP.
- **US2 (P4)**: depends on US1 (extends the facade/controller with concurrency guarantees + tests).
- **US3 (P5)**: verification is exercised continuously; formally validated after the deps are wired
  (Setup + any resolve). Independently checkable at any point after T002.
- **Polish (P6)**: after US1–US3.

### Within Each Story

- Tests (T007, T011, T012) written before/with implementation and expected to fail first.
- Facade (T008) before controller (T009); removal (T010) after the replacement compiles.

### Parallel Opportunities

- T005 / T006 (Foundational) touch different files — parallel.
- T007 is independent of T011/T012 authoring — the US1 test can be written in parallel with US2 tests.
- Polish T016 / T017 are parallel (different files).

---

## Parallel Example: Foundational

```bash
Task: "Create ServerConfig.kt (RunStore + RunApi beans) in server/src/main/kotlin/.../server/ServerConfig.kt"
Task: "Create application.yml (actuator health + store default) in server/src/main/resources/application.yml"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup (catalog + build + trusts).
2. Phase 2 Foundational (app + config beans).
3. Phase 3 US1 (facade + suspend controller + contract IT; retire the JDK server).
4. **STOP & VALIDATE**: contract IT green + `/actuator/health` UP → MVP.

### Incremental Delivery

1. Setup + Foundational → app boots.
2. US1 → contract parity on Spring Boot (MVP).
3. US2 → non-blocking concurrency proven.
4. US3 → verification enabled + covered.
5. Polish → docs, quickstart, gates.

---

## Notes

- No engine change (FR-006); only the `:server` transport migrates.
- `RunApi` / `ApiResponse` / `JsonView` are reused **unchanged** (SC-006) — do not duplicate read logic.
- Raw-JSON `ApiResponse` bodies are written straight to the response to keep the byte shape identical to
  007 (SC-001) rather than round-tripping through Jackson.
- Local builds run on Gradle 8.14.3; CI on 9.5.1 is authoritative for the new dependency graph (research R5).
- No PR for this feature yet (per standing instruction).
