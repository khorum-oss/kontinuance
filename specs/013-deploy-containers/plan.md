# Implementation Plan: Containerized Deployment — Local Compose (P1)

**Branch**: `claude/deploy-containers` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-deploy-containers/spec.md`

## Summary

Add a top-level `deploy/` directory that containerizes Kontinuance and runs it as a single-origin compose
stack, mirroring the P1 layer of relikquary's `deploy/`. Two multi-stage images (server via
`:server:installDist`; web via the Vite build served by nginx), an nginx template that fronts the API +
SSE + WebSocket on one origin, a compose file with a persisted run-store volume and a mounted descriptor,
a dev override, an `.env.example`, a deploy README, and a root `.dockerignore`. No application code
changes.

## Technical Context

**Language/Version**: Dockerfiles + nginx conf template + Compose YAML. Builds JDK 21 (Kotlin server) and
Node (SvelteKit static web).

**Primary Dependencies**: Base images only — `gradle:8.14.3-jdk21` (build), `eclipse-temurin:21-jre`
(server runtime), `node:22` (web build), `nginx:stable` (web runtime). No new Gradle/npm dependency.

**Storage**: A named Docker volume for the file-backed run store (`KONTINUANCE_STORE`).

**Testing**: `docker compose config` (always available) validates the composition; `docker compose build`
builds the images from source (dependency verification enabled). A brought-up smoke check (UI reachable,
health OK) where the environment permits.

**Target Platform**: Any Docker + Compose host; single-origin local stack.

**Project Type**: Deployment/infrastructure artifacts for the existing web-service + static SPA.

**Performance Goals**: N/A.

**Constraints**: dependency verification stays enabled in the server build (Principle V); no new
dependency (verification metadata untouched); no secrets committed (placeholder env names); no external
design link. Full image builds may be network-dependent in a sandbox; `docker compose config` is the
always-available gate (mirrors the "CI is authoritative" note for the Gradle build).

**Scale/Scope**: One `deploy/` directory (~7 files) + a root `.dockerignore`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Platform-First & Stable Public Contract**: PASS — no public contract change; the images run the
  existing server distribution and serve the existing SPA; the proxied paths match the current API/stream.
- **II. Test-First & Integration-Verified**: PASS (adapted) — no application behavior change, so no new
  integration test. Verification is `docker compose config` + an image build + a brought-up smoke check
  (`quickstart.md`).
- **III. Quality Gates Are Non-Negotiable**: PASS — no Kotlin/TS changed, so detekt/Kover/Sonar are
  unaffected and stay green. The server *image build* runs the real Gradle build with verification on.
- **IV. Correct, Covered Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — verification stays enabled in the build (`-Pdependency.env=public`,
  metadata untouched); no new dependency; no secrets committed; nothing published.

No violations → Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/013-deploy-containers/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── container-interface.md      # ports, env vars, volumes, proxied paths
└── tasks.md                        # /speckit-tasks
```

### Source Code (repository root)

```text
deploy/
├── README.md                 # NEW — build & run the compose stack (P1 scope)
├── .env.example              # NEW — KONTINUANCE_* + host ports (placeholders)
├── server.Dockerfile         # NEW — multi-stage: installDist -> JRE 21, non-root
├── web.Dockerfile            # NEW — multi-stage: vite build -> nginx
├── web/
│   └── default.conf.template # NEW — nginx: SPA + /api + SSE + /ws/runs -> ${KONTINUANCE_BACKEND}
├── docker-compose.yml        # NEW — server + web, one origin, run-store volume
└── docker-compose.dev.yml    # NEW — thin local-iteration override
.dockerignore                 # NEW (repo root) — trim the build context
```

No changes under `engine/`, `server/`, `web/`, or `gradle/`.

**Structure Decision**: A new top-level `deploy/` directory (matching relikquary) holds all container and
compose artifacts; the only file outside it is the root `.dockerignore` the image builds need. No source
module or build script is modified.

## Complexity Tracking

> No Constitution Check violations — no entries.
