# Quickstart / Verification: Containerized Deployment (P1)

## Prerequisites

- A container engine + compose tool (Docker Engine + `docker compose`).

## 1. The composition validates (SC-002, always available)

```bash
docker compose -f deploy/docker-compose.yml config
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.dev.yml config
```

Expected: both print the resolved config with no schema or interpolation errors, before anything is built.

## 2. Build the images from source (FR-001, FR-002, FR-010)

```bash
docker compose -f deploy/docker-compose.yml build
```

Expected: the server image builds via `:server:installDist` with dependency verification enabled; the web
image builds the SPA and packages nginx. (Full builds pull base images and resolve dependencies over the
network; in a restricted sandbox this may not complete — CI / an operator environment is authoritative,
same as the Gradle 9.5.1 build.)

## 3. Bring it up and reach the UI on one origin (SC-001)

```bash
cp deploy/.env.example deploy/.env      # placeholders; set a real DEPLOY_TOKEN if the example needs it
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
# open http://localhost:${KONTINUANCE_WEB_PORT:-8080}
```

Expected: the runs list loads; **RUN PIPELINE** triggers a run; a gated run can be approved from its detail
view — all on the one published URL. `curl http://localhost:8080/api/health` returns healthy.

## 4. History persists across a restart (SC-003)

```bash
docker compose -f deploy/docker-compose.yml restart
# or: down (without -v) then up — the runs list still shows prior runs
```

Expected: prior runs remain (named volume `kontinuance-runs`). `down -v` removes them.

## 5. Reconfigure without rebuilding (SC-004)

- Change `KONTINUANCE_WEB_PORT` / `KONTINUANCE_BACKEND` in `deploy/.env` → takes effect on `up`.
- Point `kontinuance.config.descriptor` at a different mounted descriptor (or use
  `docker-compose.dev.yml` to bind-mount a working copy) → a triggered run uses it, no image rebuild.

## 6. Constraints (SC-005)

- `git status` shows no `engine/`, `server/`, or `web/` application code changed (only `deploy/`, the root
  `.dockerignore`, and `specs/`).
- No secret values committed (placeholder env names only); no external design links.
