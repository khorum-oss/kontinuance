# Phase 0 Research: Containerized Deployment (P1)

Decisions are grounded in Kontinuance's real build (`server` uses the `application` plugin; `web` is a
Vite/adapter-static SPA) and in the relikquary `deploy/` parity target.

## Decision: multi-stage server image built from source via `installDist`

- **Decision**: `deploy/server.Dockerfile` — stage 1 `gradle:8.14.3-jdk21` runs
  `gradle :server:installDist -Pdependency.env=public --no-daemon`; stage 2 `eclipse-temurin:21-jre` copies
  `server/build/install/kontinuance-api/` and runs `bin/kontinuance-api` as a non-root user.
- **Rationale**: The server has no `bootJar`; `installDist` is the project's documented distribution
  (`applicationName = kontinuance-api`, `mainClass = …KontinuanceApiApplicationKt`). Building in-image
  keeps `docker compose up --build` self-contained. Gradle 8.14.3 (not the pinned 9.5.1 wrapper) matches
  the version the project builds with locally and avoids a wrapper download in the build.
- **Verification**: `-Pdependency.env=public` matches CI and resolves from public repos with
  `verification-metadata.xml` **enabled** (Principle V). The container sets `SERVER_ADDRESS=0.0.0.0` so it
  listens on all interfaces *inside* the container (the host still only exposes what compose publishes).
- **Alternatives**: pre-built dist copied in (needs a host build step first — rejected for
  self-containment); a fat-jar (no `bootJar` exists — rejected).

## Decision: web image = Vite build served by nginx, proxying on one origin

- **Decision**: `deploy/web.Dockerfile` — stage 1 `node:22` runs `corepack`/`pnpm install --frozen-lockfile`
  + `pnpm build` → `web/build/`; stage 2 `nginx:stable` copies `web/build/` and
  `deploy/web/default.conf.template`. nginx serves the SPA and proxies `/api`, `/api/runs/stream`, and
  `/ws/runs` to the server.
- **Rationale**: adapter-static emits a client-rendered SPA (`fallback: index.html`), so nginx is the
  natural server; putting the proxy in the web container gives the browser one origin with no CORS.
- **Template + envsubst**: `default.conf.template` uses `${KONTINUANCE_BACKEND}`; nginx's official image
  renders `/etc/nginx/templates/*.template` through `envsubst` at start, so the backend target is set at
  run time (FR-003) without rebuilding.
- **Alternatives**: a Node static server (heavier, no built-in proxy — rejected); baking the backend URL
  (needs rebuild to repoint — rejected).

## Decision: nginx proxy specifics (SSE + WebSocket)

- **Decision**: In the template — `location /` → `try_files $uri /index.html`; a `location =
  /api/runs/stream` block with `proxy_buffering off` and a long read timeout; a `location /api/` block;
  and a `location /ws/runs` block with `Upgrade`/`Connection: upgrade` and HTTP/1.1.
- **Rationale**: the live SSE stream must not be buffered; the WebSocket (`/ws/runs`, confirmed in
  `WebSocketConfig`) needs the upgrade headers. This mirrors the host-proxy example already in the repo
  (`docs/examples/nginx.conf`), now templated for the container.

## Decision: compose wires one origin, a persisted run store, and a mounted descriptor

- **Decision**: `deploy/docker-compose.yml` — a `server` service (build `server.Dockerfile`, context repo
  root, `KONTINUANCE_STORE=/data/runs` on a named volume `kontinuance-runs`, the example descriptor mounted
  read-only at a fixed path and pointed to by `kontinuance.config.descriptor`) and a `web` service (build
  `web.Dockerfile`, `KONTINUANCE_BACKEND=http://server:8077`, published on `${KONTINUANCE_WEB_PORT:-8080}`).
  Only `web` is published, so the app is one origin.
- **Rationale**: FR-004/005/006 — one URL, persisted history, descriptor without a rebuild. The build
  context is the repo root (both Dockerfiles need module sources); the root `.dockerignore` trims it.
- **Dev override** (`docker-compose.dev.yml`): publishes the server port directly and bind-mounts a local
  descriptor for iteration, without editing the base file (FR-009).
- **Alternatives**: publishing both services (two origins → CORS — rejected); baking the descriptor
  (rebuild to change — rejected).

## Decision: verification approach given sandbox limits

- **Decision**: `docker compose config` is the always-runnable gate (validates schema + interpolation
  offline). Attempt `docker compose build` and a brought-up smoke check; if the image build is
  network-limited in the sandbox, record that and rely on correctness-by-construction + CI, exactly as the
  project already treats the Gradle 9.5.1 build as CI-authoritative.
- **Rationale**: honest verification without pretending a heavyweight, network-bound build always runs
  here.
