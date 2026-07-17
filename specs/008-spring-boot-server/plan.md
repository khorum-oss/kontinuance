# Implementation Plan: Spring Boot Server (Coroutine API Runtime)

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` (008 numbers the specs dir only) | **Date**: 2026-07-15 | **Spec**: [spec.md](./spec.md)

## Summary

Migrate the `:server` module from the Spring-free JDK `HttpServer` (007) to **Spring Boot 4.1.0**
(WebFlux + actuator) with **Kotlin coroutines** — suspend `@RestController` handlers reusing the 007
`RunApi` behind a suspend `RunReadFacade`. The public `/api` contract is unchanged. Dependency
verification stays enabled, extended by 13 group trusts (empirically confirmed in research R2).

## Technical Context

**Language/Version**: Kotlin 2.3.21 / JDK 21. **Runtime**: Spring Boot **4.1.0** (WebFlux + actuator) —
the constitution's named platform runtime, now entering the codebase.

**Primary Dependencies** (via the version catalog + BOM): `spring-boot-starter-webflux`,
`spring-boot-starter-actuator`, `spring-boot-starter-test` + `reactor-test` (test); plugins
`org.springframework.boot`, `io.spring.dependency-management`, `org.jetbrains.kotlin.plugin.spring`.
`:persistence` (`RunStore`) and the catalog's `kotlinx-coroutines-core` + `serialization-json`.

**Storage**: reads the 006 `RunStore` (file-backed) behind a suspend facade; no store of its own.

**Testing**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient` real HTTP round-trip
(Constitution II — the real runtime, not mocked).

**Target Platform**: JVM Spring Boot service (private network).

**Constraints**: `/api` contract unchanged (Constitution I); verification stays enabled (Principle V —
extended, never disabled); suspend handlers, one offloaded blocking hop (FR-003). Local build on Gradle
8.14.3; CI on 9.5.1 authoritative for the new dependency graph (research R5).

## Constitution Check

- **I. Stable Public Contract** — PASS. `/api` routes + JSON shape preserved exactly; a test pins them.
- **II. Test-First & Integration-Verified** — PASS. `@SpringBootTest` + real HTTP round-trip on the
  actual runtime.
- **III. Quality Gates** — PASS. Module stays under detekt/Kover; gates green.
- **IV. Code Generation** — N/A.
- **V. Supply-Chain Integrity** — PASS / central to this feature. New deps via the catalog; verification
  stays **enabled**, extended with group trusts (research R2), never disabled/baselined; no secrets added.

**Result: PASS.**

## Project Structure

```text
server/                                         # migrated in place
├── build.gradle.kts        # + spring-boot / dependency-management / kotlin-spring plugins; webflux+actuator; boot main
└── src/{main,test}/kotlin/org/khorum/oss/kontinuance/server/
    ├── RunApi.kt           # UNCHANGED (007) — reused read logic
    ├── ApiResponse.kt, JsonView.kt   # reused
    ├── RunReadFacade.kt    # NEW — suspend wrapper: withContext(Dispatchers.IO) over RunApi
    ├── RunController.kt     # NEW — suspend @RestController for /api/health,/api/runs,/api/runs/{id}
    ├── ServerConfig.kt      # NEW — @Configuration: RunStore bean from `kontinuance.store` property
    └── KontinuanceApiApplication.kt  # NEW — @SpringBootApplication main
    # REMOVED: HttpApiServer.kt, cli/ApiServerMain.kt (replaced by the Spring app + boot server)

gradle/libs.versions.toml            # + springBoot/springDependencyManagement versions, plugins, starters
gradle/verification-metadata.xml     # + 13 Spring-ecosystem group trusts (research R2)
```

**Structure Decision**: migrate the existing `:server` module in place. `RunApi`/`ApiResponse`/`JsonView`
are reused unchanged; the JDK transport (`HttpApiServer` + `ApiServerMain`) is replaced by a Spring Boot
app (`KontinuanceApiApplication`) + a suspend controller over the `RunReadFacade`. Engine untouched.

## Complexity Tracking

No violations — section intentionally empty. (Spring Boot is the constitution's designated runtime; its
adoption is the point of the feature, not unjustified complexity.)
