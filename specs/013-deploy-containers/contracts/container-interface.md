# Contract: Container Interface (source of truth)

What the images and the compose stack expose. The Dockerfiles/compose MUST match this; it traces to the
server's real config (`application.yml`, controllers, `WebSocketConfig`) and the web build.

## Server image

| Aspect | Value | Notes |
|---|---|---|
| Runtime | `eclipse-temurin:21-jre`, non-root user | built from `:server:installDist` |
| Entrypoint | `bin/kontinuance-api` | `application` plugin launcher (`applicationName`) |
| Listen | `0.0.0.0:8077` | `SERVER_ADDRESS=0.0.0.0` inside container; host exposes only via compose |
| Run store | `KONTINUANCE_STORE=/data/runs` | mounted volume |
| Descriptor | `kontinuance.config.descriptor=/etc/kontinuance/kontinuance.yml` | mounted read-only |
| Health | `GET /api/health` (and `/actuator/health`) | actuator exposes health |
| Secrets | pipeline `secrets:` via env vars | never baked into the image |

## Web image

| Aspect | Value | Notes |
|---|---|---|
| Build | `pnpm build` → `web/build/` (adapter-static SPA) | `ssr=false`, `index.html` fallback |
| Runtime | `nginx:stable` | serves `web/build/`, renders `default.conf.template` via envsubst |
| Proxy target | `${KONTINUANCE_BACKEND}` (e.g. `http://server:8077`) | set at container start |
| Listen | `:80` in container, published on host | one origin for UI + API |

## Proxied paths (one origin)

| Path | Kind | nginx handling |
|---|---|---|
| `/` and unknown | static | `try_files $uri /index.html` (SPA routing) |
| `/api/**` | HTTP | `proxy_pass` to backend |
| `/api/runs/stream` | SSE | `proxy_pass` + `proxy_buffering off`, long read timeout |
| `/ws/runs` | WebSocket | `proxy_pass` + `Upgrade`/`Connection: upgrade`, HTTP/1.1 |

## Compose stack env (`.env.example`)

| Variable | Meaning | Example (placeholder) |
|---|---|---|
| `KONTINUANCE_WEB_PORT` | host port the UI is published on | `8080` |
| `KONTINUANCE_BACKEND` | web → server target (internal) | `http://server:8077` |
| `KONTINUANCE_STORE` | run-store path inside the server container | `/data/runs` |
| `KONTINUANCE_CONFIG_DESCRIPTOR` | descriptor path inside the server container | `/etc/kontinuance/kontinuance.yml` |
| `DEPLOY_TOKEN` (example) | a pipeline secret consumed by the example descriptor | `changeme` |

Named volume: `kontinuance-runs` → the server's `KONTINUANCE_STORE` (persists run history).
