# Feature Specification: Spring Boot Server (Coroutine API Runtime)

**Feature Branch**: `008-spring-boot-server`

**Created**: 2026-07-15

**Status**: Draft

**Input**: User description: "Migrate the 007 read API from the Spring-free JDK HttpServer to real Spring Boot — the platform runtime the constitution names — using Kotlin structured concurrency (suspend). Same public /api contract + actuator health, served from a Spring Boot app with coroutine-based (suspend) controller handlers on the reactive stack so handling is non-blocking; reuse the transport-agnostic RunApi over the RunStore behind a suspend read facade. Introduce Spring Boot 4.1.0 (+ kotlin-spring + dependency-management), starter-webflux + starter-actuator. Dependency verification MUST stay enabled: extend verification-metadata.xml with group trusts for the Spring ecosystem rather than disabling it. Tests use @SpringBootTest + a real HTTP round-trip. Full local verification is limited by the sandbox; CI is the authoritative gate. Out of scope: SSE/WebSocket, manual-trigger POST, auth."

## User Scenarios & Testing *(mandatory)*

The 007 read API proved the surface on a minimal Spring-free HTTP server. This feature moves it onto the platform runtime the project is built for — a Spring Boot application with Kotlin coroutines — so the service is production-shaped (dependency injection, configuration, health/observability actuator, non-blocking request handling) and ready to grow the streaming and write endpoints the UI needs. The public API is unchanged, so no consumer breaks. "Users" are the API's consumers (the Web UI, operators) whose experience must be identical, and the maintainers who now run a real application runtime.

### User Story 1 - The same API, now on the platform runtime (Priority: P1)

A consumer calls the existing endpoints (health, list runs, run by id) and gets identical behavior and response shapes — but the service is now a Spring Boot application (with actuator health) rather than the minimal server.

**Why this priority**: The migration's whole point is to preserve the contract while changing the runtime. If behavior changed, consumers would break; if the runtime didn't change, there'd be no feature.

**Independent Test**: Start the Spring Boot service over a store with runs; hit the endpoints and assert the same responses as before (list newest-first bounded, detail by id, 404 for unknown), plus the framework health endpoint reports up.

**Acceptance Scenarios**:

1. **Given** a store with runs, **When** the list/detail/health endpoints are called on the running Spring Boot service, **Then** the responses match the established `/api` contract exactly (shape, ordering, bounds, 404s).
2. **Given** the running service, **When** the framework's health/observability endpoint is requested, **Then** it reports the application is up.
3. **Given** the migration, **When** a previously-written consumer calls the API, **Then** it works unchanged (no contract break).

### User Story 2 - Non-blocking request handling with structured concurrency (Priority: P2)

Requests are handled with Kotlin structured concurrency (suspending handlers) so a slow read does not tie up a request thread, and the API composes cleanly with the coroutine-based engine — setting up the later live-streaming endpoints.

**Why this priority**: Non-blocking handling is the reason to adopt the reactive runtime now (and the seam for future streaming); it builds on US1 but is a distinct quality/architecture outcome.

**Independent Test**: Exercise the endpoints concurrently and assert correct responses; confirm handlers are suspending and reads cross a suspend boundary (no blocking call on the request path other than inside the offloaded facade).

**Acceptance Scenarios**:

1. **Given** multiple simultaneous requests, **When** they are served, **Then** each returns the correct response without interference.
2. **Given** the read path, **When** it accesses the (blocking) store, **Then** it does so behind a suspending boundary that offloads blocking work, keeping request handling non-blocking.

### User Story 3 - Verification stays enabled through the dependency change (Priority: P1)

Introducing the application framework brings many new dependencies; the supply-chain gate (dependency verification) MUST remain enabled and pass, extended to trust the new ecosystem rather than switched off.

**Why this priority**: Constitution Principle V is non-negotiable — a large dependency addition is exactly when verification matters most. Turning it off to make the build pass is forbidden.

**Independent Test**: With verification enabled, resolve and build the service; confirm the new dependencies are covered by verification (group trusts added) and that verification is not disabled anywhere.

**Acceptance Scenarios**:

1. **Given** the new dependencies, **When** the build resolves them with verification enabled, **Then** they are trusted via added metadata (no `verification=false`, no removed gate).
2. **Given** the change, **When** the supply-chain configuration is reviewed, **Then** verification (checksums/signatures) remains enabled and no secrets are added.

### Edge Cases

- A new transitive dependency group is not yet trusted → the build fails verification (correctly), signalling the metadata must be extended; it is never resolved by disabling verification.
- The service cannot bind its port → it fails to start with a clear error, as before.
- The store is empty/missing at startup → the service still starts and serves an empty list.
- Local build runs on a different build-tool version than CI → the authoritative verification of the new dependency graph is CI (documented), not the local sandbox.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The service MUST serve the existing `/api` read contract (health, list runs newest-first + bounded, run by id with 404 for absent) with byte-for-byte-compatible behavior and response shapes — no consumer-visible change.
- **FR-002**: The service MUST run as an application on the project's designated platform runtime (Spring Boot), including a framework health/observability endpoint (actuator).
- **FR-003**: Request handlers MUST use Kotlin structured concurrency (suspending functions); the read path MUST cross a suspending boundary that offloads the blocking store access so request handling is non-blocking.
- **FR-004**: The transport-agnostic read logic (the 007 `RunApi` over `RunStore`) MUST be reused behind the controllers (no duplicated read logic).
- **FR-005**: New dependencies MUST be added through the version catalog; dependency **verification MUST remain enabled** and be extended (group trusts) to cover the new ecosystem — verification MUST NOT be disabled, baselined, or removed (Constitution V).
- **FR-006**: The migration MUST NOT change the pipeline engine's public contract; it replaces only the API transport.
- **FR-007**: The service MUST be startable as a runnable application bound to a configurable address, reading the run store location used by the CI service.
- **FR-008**: The API MUST be integration-tested on the real runtime (application context + a real HTTP round-trip) so behavior is verified against the framework, not mocked (Constitution II).
- **FR-009**: No secret values appear in any response or are added to the repository through the new dependencies/config.

### Key Entities

- **Run resource / API contract**: unchanged from 007 (the `/api` routes + JSON shape) — a stable consumer contract preserved across the runtime change.
- **Read facade**: the suspending boundary that exposes the (blocking) `RunStore` reads to coroutine handlers by offloading blocking work.
- **Supply-chain trust set**: the verification-metadata group trusts extended to cover the new framework ecosystem.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the existing `/api` responses are unchanged after the migration (same shape, ordering, bounds, status codes) — 0 consumer-visible differences.
- **SC-002**: The framework health endpoint reports up on the running application.
- **SC-003**: Request handlers are suspending and the blocking store access is offloaded (0 blocking store calls directly on the request-handling thread).
- **SC-004**: Dependency verification remains enabled and passes for the new dependency graph (0 instances of verification being disabled/removed); new deps are trusted via added metadata.
- **SC-005**: The API is verified on the real application runtime via an integration test performing a real HTTP round-trip.
- **SC-006**: The read logic (`RunApi`) is reused unchanged (0 duplicated read implementations).
- **SC-007**: No secret values are added to the repository or exposed by any response.

## Assumptions

- The platform runtime is Spring Boot aligned with the khorum stack (the sibling app's Spring Boot 4.x line + the Kotlin Spring plugin), on the reactive/coroutine stack so suspending handlers are first-class; this is the constitution's named runtime finally entering the codebase.
- The blocking file-backed `RunStore` (006) is wrapped behind a suspending facade (offloading blocking IO) rather than rewritten; a future non-blocking backend can replace it behind the same facade.
- Dependency verification is extended by trusting the new dependency groups (the same mechanism the repo already uses for other ecosystems), not by per-artifact regeneration — feasible without external key-server access.
- Full local build/verification is constrained by the sandbox (the pinned build-tool version's distribution is blocked, so local runs use an older version); CI is the authoritative gate for the new dependency graph, a constraint explicitly accepted for this feature.
- The public `/api` contract and consumers are unchanged; SSE/WebSocket streaming, write endpoints (manual trigger), and authentication remain out of scope (follow-ups).
