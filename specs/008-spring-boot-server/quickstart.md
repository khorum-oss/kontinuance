# Quickstart: Spring Boot Server

## Run it
```bash
./gradlew :server:run                                       # Spring Boot app (application plugin), serves ~/.kontinuance/runs on :8077
# config: --args='--server.port=9000 --kontinuance.store=/path/to/runs'  (or env KONTINUANCE_STORE, SERVER_PORT)
# or install the launcher: ./gradlew :server:install && kontinuance-api --server.port=9000
# (no Boot Gradle plugin / no bootRun — the app runs via the application plugin's main; research R2)
```

## Validate (US1–US3)
```bash
curl -s localhost:8077/api/health                 # {"status":"ok"}      (unchanged /api contract)
curl -s localhost:8077/actuator/health            # {"status":"UP"}      (framework health, US1/FR-002)
curl -s "localhost:8077/api/runs?limit=20"        # {"runs":[ …newest first… ]}
curl -s localhost:8077/api/runs/<id>              # a run, or 404
```
Responses match 007 exactly (SC-001). Handlers are `suspend` and the store read is offloaded via
`withContext(Dispatchers.IO)` (FR-003).

## Automated verification
`./gradlew :server:test` — `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `WebTestClient` real HTTP
round-trip over a temp store (health, list, detail, 404s). `./gradlew build` stays green with
**dependency verification enabled** (extended via group trusts; research R2). Note: local build on Gradle
8.14.3; CI on 9.5.1 is authoritative for the new dependency graph.
