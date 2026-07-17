# Contract: Configuration Surface (source of truth)

This is the authoritative list the guide (`docs/running.md`) MUST match (FR-001, SC-002). Every row is
traced to code; if code changes, this table and the guide change together.

## Server (Spring Boot)

| Setting (property) | Env override | Default | Source | Effect |
|---|---|---|---|---|
| `server.address` | `SERVER_ADDRESS` | `127.0.0.1` | `application.yml` | Bind address; loopback by default |
| `server.port` | `SERVER_PORT` | `8077` | `application.yml` | Listen port |
| `kontinuance.store` | `KONTINUANCE_STORE` | `${user.home}/.kontinuance/runs` | `application.yml` + `ServerConfig` | Run-history directory (`FileRunStore`) |
| `kontinuance.config.descriptor` | (relaxed binding) | `kontinuance.yml` | `ConfigController`, `RunTrigger`, `RunApprovals` | Pipeline descriptor loaded for `/api/config` and triggered/resumed runs |
| `kontinuance.coverage.report` | (relaxed binding) | `build/reports/kover/report.xml` | `CoverageController` | Kover XML surfaced by `/api/coverage` |
| `kontinuance.stream.poll-interval-ms` | (relaxed binding) | `1000` | `application.yml` | How often the SSE/WS stream re-reads the store |
| `kontinuance.stream.snapshot-limit` | (relaxed binding) | `50` | `application.yml` | Newest-first snapshot size sent on stream connect |
| `management.endpoints.web.exposure.include` | — | `health` | `application.yml` | Actuator exposes health only |
| pipeline `secrets:` | one env var per secret name | — | engine `EnvSecretSource` | Step secrets resolved from process env; missing secret fails the run |

## Web app (static SPA)

| Setting | Where | Default | Effect |
|---|---|---|---|
| `KONTINUANCE_API` | `web/vite.config.ts` (dev only) | `http://localhost:8077` | Dev-server proxy target for `/api` + `/ws` |
| build output | adapter-static | `web/build/` (SPA, `index.html` fallback, `ssr=false`) | Static assets to serve in production |

## Endpoints the proxy must route (same origin)

| Path | Kind | Notes |
|---|---|---|
| `/api/**` | HTTP | Read API + `POST /api/runs/trigger`, `/api/runs/{id}/approve`, `/api/runs/{id}/reject` |
| `/api/runs/stream` | HTTP (SSE) | Server-sent events; disable proxy buffering |
| `/ws/runs` | WebSocket | Requires `Upgrade`/`Connection` headers |
| everything else | static | Served from `web/build/`, with `index.html` fallback for client routing |
