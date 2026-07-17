# Deploying Kontinuance

Container and Compose artifacts for running Kontinuance. This is the **P1 slice**: two images and a
single-origin local stack. Kubernetes, ArgoCD/GitOps, stage→prod promotion scripts, and authentication
are **later slices** — not included here.

For the full runtime configuration surface (every setting and its default), the serving model, and the
operational limitations, see [`../docs/running.md`](../docs/running.md).

## Layout

| File | What it is |
|---|---|
| `server.Dockerfile` | Multi-stage server image: builds `:server:installDist`, runs it on a JRE as non-root |
| `web.Dockerfile` | Multi-stage web image: builds the SPA, serves it with nginx |
| `web/default.conf.template` | nginx site — SPA + `/api` + SSE + `/ws/runs`, proxied to `${KONTINUANCE_BACKEND}` on one origin |
| `docker-compose.yml` | Brings up server + web on one origin, with a persisted run-store volume |
| `docker-compose.dev.yml` | Thin override that also publishes the server port directly |
| `kontinuance.yml` | Default demo pipeline descriptor (mounted read-only) |
| `.env.example` | Copyable settings (placeholder values) |

## Quick start

```bash
# 1. Configure (placeholders are fine to start; set a real DEPLOY_TOKEN for the demo deploy step).
cp deploy/.env.example deploy/.env

# 2. Validate the composition (no build needed).
docker compose -f deploy/docker-compose.yml config

# 3. Build and run.
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build -d

# 4. Open the UI (single origin).
#    http://localhost:8080   (or your KONTINUANCE_WEB_PORT)
```

In the UI: the runs list loads from the live API, **RUN PIPELINE** triggers a run, and a run that reaches
the approval gate can be approved from its detail view.

Health check: `curl http://localhost:8080/api/health`.

## How it fits together

- Only the **web** service is published. It serves the static SPA and reverse-proxies `/api`, the SSE
  stream (`/api/runs/stream`), and the WebSocket (`/ws/runs`) to the **server** over the internal
  network — so the browser uses one origin with no CORS setup.
- The server keeps run history in the named volume `kontinuance-runs`, so it survives restarts
  (`docker compose down` keeps it; `down -v` removes it).
- The pipeline descriptor is mounted read-only from `deploy/kontinuance.yml`. Point at your own by editing
  that mount, or use the dev override to bind-mount a working copy. Pipeline secrets (e.g. `DEPLOY_TOKEN`)
  come from the environment and are never baked into an image.

## Configuration

All settings come from `deploy/.env` (see `.env.example`): `KONTINUANCE_WEB_PORT`, `KONTINUANCE_BACKEND`,
`KONTINUANCE_STORE`, `KONTINUANCE_CONFIG_DESCRIPTOR`, and any pipeline secret names. The full server
configuration surface is documented in [`../docs/running.md`](../docs/running.md).

## Local iteration

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.dev.yml up --build
```

The dev override additionally publishes the server on `KONTINUANCE_SERVER_PORT` (default 8077) so you can
reach the API directly.

## Notes

- The server image builds the project with dependency verification **enabled**; a full build resolves
  dependencies from the public repositories over the network.
- No authentication yet — the stack is intended for local/trusted use. Front it with an authenticating
  proxy before exposing it (see the limitations in `../docs/running.md`).
