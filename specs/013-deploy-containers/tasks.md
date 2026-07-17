# Tasks: Containerized Deployment — Local Compose (P1)

**Feature**: 013-deploy-containers | **Branch**: `claude/deploy-containers`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Container-and-compose feature — **no engine/server/web application code changes** (only `deploy/`, a root
`.dockerignore`, and this spec dir). Interface traces to
[contracts/container-interface.md](./contracts/container-interface.md).

**MVP** = User Story 1 (one command → working app on one origin).

---

## Phase 1: Setup

- [x] T001 [P] Create the top-level `deploy/` directory (and `deploy/web/`)
- [x] T002 [P] Add a repo-root `.dockerignore` excluding `**/build`, `**/node_modules`, `.git`, `.gradle`, `web/build`, IDE/OS cruft — trims the image build context (FR-012)

## Phase 2: Foundational (blocking prerequisites)

- [x] T003 Re-verify [contracts/container-interface.md](./contracts/container-interface.md) against the live sources (`server/build.gradle.kts` applicationName/mainClass, `application.yml`, `web/vite.config.ts`, `WebSocketConfig`) so the Dockerfiles/compose target real values — blocks the image and compose tasks

---

## Phase 3: User Story 1 — One command → working app on one origin (Priority: P1) 🎯 MVP

**Goal**: `docker compose ... up --build` yields a working UI at one URL, history persisted.
**Independent test**: bring the stack up; the runs list loads, a run triggers, a gated run approves.

- [x] T004 [P] [US1] Write `deploy/server.Dockerfile`: multi-stage — `gradle:8.14.3-jdk21` runs `gradle :server:installDist -Pdependency.env=public --no-daemon`; `eclipse-temurin:21-jre` copies `server/build/install/kontinuance-api/`, runs `bin/kontinuance-api` as a non-root user, `SERVER_ADDRESS=0.0.0.0`, `EXPOSE 8077` (FR-001, FR-010)
- [x] T005 [P] [US1] Write `deploy/web/default.conf.template`: nginx serving the SPA (`try_files $uri /index.html`), `location = /api/runs/stream` with `proxy_buffering off` + long read timeout, `location /api/`, and `location /ws/runs` with the WebSocket upgrade — all `proxy_pass` to `${KONTINUANCE_BACKEND}` (FR-002, FR-003)
- [x] T006 [P] [US1] Write `deploy/web.Dockerfile`: multi-stage — `node:22` runs `corepack enable` + `pnpm install --frozen-lockfile` + `pnpm build` (→ `web/build/`); `nginx:stable` copies `web/build/` to the web root and `default.conf.template` to `/etc/nginx/templates/` (envsubst renders it at start) (FR-002)
- [x] T007 [US1] Write `deploy/docker-compose.yml`: `server` service (build `server.Dockerfile`, context repo root; `KONTINUANCE_STORE=/data/runs` on named volume `kontinuance-runs`; example descriptor mounted read-only at `/etc/kontinuance/kontinuance.yml` with `kontinuance.config.descriptor` set to it) + `web` service (build `web.Dockerfile`; `KONTINUANCE_BACKEND=http://server:8077`; published on `${KONTINUANCE_WEB_PORT:-8080}`); only `web` is published (FR-004, FR-005, FR-006)

**Checkpoint**: US1 complete — the app runs from one command on one origin.

---

## Phase 4: User Story 2 — Configure without editing images (Priority: P2)

**Goal**: repoint descriptor/port/backend and supply secrets via env + mounts, no rebuild.
**Independent test**: set values in `.env`, bring up, trigger a run using that descriptor + secret.

- [x] T008 [P] [US2] Write `deploy/.env.example`: placeholder `KONTINUANCE_WEB_PORT`, `KONTINUANCE_BACKEND`, `KONTINUANCE_STORE`, `KONTINUANCE_CONFIG_DESCRIPTOR`, and an example pipeline secret (`DEPLOY_TOKEN=changeme`) — no real secrets (FR-007, FR-008)
- [x] T009 [US2] Ensure `deploy/docker-compose.yml` reads those env vars (published port, backend, store, descriptor) and passes pipeline secrets through to the `server` service from the environment — no secret baked into any image (FR-007)

**Checkpoint**: the stack is configurable via `.env` + mounts alone.

---

## Phase 5: User Story 3 — Local iteration override (Priority: P3)

**Goal**: a thin override for local iteration that leaves the base file untouched.
**Independent test**: apply base + override; the override's effect (e.g. server port reachable directly) holds.

- [x] T010 [P] [US3] Write `deploy/docker-compose.dev.yml`: an override that publishes the `server` port directly and bind-mounts a local working-copy descriptor for iteration, without modifying `docker-compose.yml` (FR-009)

---

## Phase 6: Polish & Cross-Cutting

- [x] T011 [US1] Write `deploy/README.md`: how to `config`/`build`/`up` the stack, the `.env` workflow, the persisted-volume note, and an explicit "k8s / ArgoCD / promotion scripts / authentication are later slices" statement (FR-011); reference `docs/running.md` for the config surface
- [x] T012 Verify: `docker compose -f deploy/docker-compose.yml config` and the base+dev override both validate (SC-002); attempt `docker compose build` and a brought-up smoke check (`/api/health`), recording the result honestly if the sandbox limits the build (quickstart §2–3)
- [x] T013 [P] Confirm no secrets and no external design links in any `deploy/**` file (placeholder env names only), and that `git status` shows no `engine/`/`server/`/`web/` application code changed (FR-012, FR-013, SC-005)

---

## Dependencies

- **Setup (T001–T002)** → before images/compose.
- **Foundational (T003)** → before T004–T007.
- **US1 (T004–T007)**: T004/T005/T006 are `[P]` (separate files); T007 (compose) depends on the image
  definitions and the nginx template existing.
- **US2 (T008–T009)**: T009 edits the compose file from T007 (sequential on that file).
- **US3 (T010)**: independent override file (`[P]`).
- **Polish (T011–T013)** → after the stack files exist.

## Parallel Opportunities

- `[P]`: T001, T002 (setup); T004, T005, T006 (server image / nginx template / web image); T008 (env
  example); T010 (dev override); T013 (read-only checks). `docker-compose.yml` tasks (T007, T009) are not
  `[P]` with each other.

## Implementation Strategy

- **MVP first**: Phase 1–3 (US1) → one-command runnable stack on one origin. Ship-able alone.
- **Increment**: US2 (env config), US3 (dev override), then Polish (README + verification).
- **Verification gate**: `docker compose config` always runs (T012); the full image build + smoke check
  runs where the environment allows, otherwise CI/operator env is authoritative.
