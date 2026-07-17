# Phase 0 Research: Server / Read API

## R1 — HTTP transport: JDK HttpServer vs. Spring Boot (this increment)

**Decision**: JDK `com.sun.net.httpserver.HttpServer` for this increment.
**Rationale**: zero new dependency, so `verification-metadata.xml` is untouched (Principle V) — Spring
Boot pulls dozens of transitive artifacts (spring-*, tomcat, etc.) from groups not currently trusted,
requiring a metadata regeneration that the current sandbox cannot perform (pinned Gradle 9.5.1
distribution blocked, key-servers disabled). The read API is small (3 GET routes); `HttpServer` serves
it fully and is already used successfully as a test seam in `:github`. Read logic is isolated behind
`RunApi`, so adopting Spring Boot later is a transport swap, not a rewrite.
**Alternatives**: Spring Boot now (rejected this increment — dependency/verification cost, unverifiable
here); Ktor/http4k (rejected — still a new dependency).

## R2 — JSON responses without a new dependency

**Decision**: build responses with the catalog's runtime `kotlinx-serialization-json`
(`RunRecord.toJson()` already exists; `buildJsonArray` for the list) — no `@Serializable`/compiler
plugin, consistent with `:github` and `:persistence`.
**Alternatives**: Jackson (new dep); hand-rolled strings (fragile).

## R3 — Handler/transport separation (FR-008 / SC-007)

**Decision**: `RunApi` returns a plain `ApiResponse(status, json)` for `health`/`listRuns`/`getRun`;
`HttpApiServer` maps HTTP requests → `RunApi` calls → HTTP responses. Handlers are unit-tested with no
server; the server is tested via a real `HttpClient` round-trip.
**Rationale**: proves the seam a future Spring/SSE transport reuses, and keeps the HTTP layer trivial.

## R4 — Limit handling (FR-002)

**Decision**: `?limit=N` parsed leniently — absent/invalid → default (50); clamped to a max cap (500)
so a response is never unbounded. Documented in the contract.

## R5 — Bind address + config (FR-007)

**Decision**: host/port from CLI args or env (`KONTINUANCE_API_HOST`/`KONTINUANCE_API_PORT`), default
`127.0.0.1:8077`; store dir defaults to `~/.kontinuance/runs` (where the CI service records). A bind
failure exits with a clear message. No auth (private network — an explicit assumption/follow-up).
