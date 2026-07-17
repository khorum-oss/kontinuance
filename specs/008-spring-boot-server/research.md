# Phase 0 Research: Spring Boot Server

## R1 — Runtime: Spring Boot 4.1.0 on the reactive (WebFlux) + coroutine stack

**Decision**: Spring Boot **4.1.0** (matches the khorum sibling app's line) with **WebFlux** +
**actuator**, Kotlin coroutines via **suspend `@RestController`** handlers. Applied through the
`org.springframework.boot` + `io.spring.dependency-management` (BOM) + `org.jetbrains.kotlin.plugin.spring`
Gradle plugins, added to the version catalog.
**Rationale**: WebFlux is the non-blocking stack where suspend handlers are first-class, consistent with
the coroutine engine and the natural base for later `Flow`-based SSE. Spring Boot 4.1 is Kotlin-2.3.21-
compatible (proven in the sibling app).
**Alternatives**: Spring MVC (blocking; suspend supported but thread-per-request) — rejected for the
streaming trajectory; staying Spring-free (007) — this feature is exactly the deliberate move off it.

## R2 — Dependency verification MUST stay enabled (Constitution V) — EMPIRICALLY PROBED

**Decision**: Keep verification enabled and extend `gradle/verification-metadata.xml` with **group
trusts** for the Spring ecosystem — the same mechanism the repo already uses for `org.jetbrains`,
`com.google`, etc. **Probed on Gradle 8.14.3**: added trusts, resolved `spring-boot-starter-webflux`,
iterated until `:server:compileKotlin` was `BUILD SUCCESSFUL` with verification on. The **complete set
of trusts needed** (13):
`org.springframework(.*)`, `io.micrometer(.*)`, `io.projectreactor(.*)`, `org.reactivestreams(.*)`,
`ch.qos.logback(.*)`, `jakarta(.*)`, `tools.jackson(.*)` (Jackson **3.x** moved group from
`com.fasterxml`), `org.yaml(.*)`, `org.osgi(.*)`, `org.bouncycastle(.*)`, `org.apache.logging(.*)`
(the existing `org.apache` trust is an **exact** group, so it does not cover `org.apache.logging.log4j`),
`biz.aQute.bnd(.*)`, and `commons-logging`. (`org.apache.tomcat` is not pulled — WebFlux uses Netty via
`io.projectreactor`; `com.fasterxml`/`org.apache` were already trusted.)
**Rationale**: no key-server access is needed (group trust, not per-artifact PGP regeneration), so it is
achievable in-sandbox and mirrors existing metadata. Verification is never disabled/baselined.
**Alternatives**: `--write-verification-metadata sha256,pgp` — rejected (key-servers disabled, and the
pinned Gradle 9.5.1 is blocked here). Disabling verification — forbidden (Principle V).

## R3 — Reuse the 007 read logic behind a suspend facade

**Decision**: keep the transport-agnostic `RunApi` (pure logic) and add a **`RunReadFacade`** with
`suspend` methods that call `RunApi` inside `withContext(Dispatchers.IO)` (the file store is blocking).
The suspend `@RestController` calls the facade. Retire the JDK `HttpApiServer` + `ApiServerMain`
(replaced by the Spring app + boot server).
**Rationale**: no duplicated read logic (FR-004/SC-006); request handling is non-blocking with the one
blocking hop offloaded (FR-003).

## R4 — Config

**Decision**: Spring config — `server.port`/host via Spring properties/env; store dir via a property
(`kontinuance.store` / `KONTINUANCE_STORE`, default `~/.kontinuance/runs`) bound to a `RunStore` bean.
Actuator exposes `/actuator/health` (FR-002).

## R5 — Local verification limits

**Decision**: build/tests run on Gradle **8.14.3** locally (the wrapper's 9.5.1 distribution host is
blocked); the new dependency graph's authoritative verification is **CI on 9.5.1**. Recorded per the
accepted constraint. The probe above establishes local feasibility (resolve + verify + compile green).
