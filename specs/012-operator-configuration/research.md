# Phase 0 Research: Operator Configuration & Deployment Guide

All "unknowns" here are *what the current implementation actually does* — resolved by reading source, not
by choosing new behavior. This feature documents; it does not decide runtime behavior.

## Decision: source every configuration value from the running code, not from memory

- **Decision**: The guide and `contracts/config-surface.md` enumerate exactly the settings the server and
  web app read, each traced to its source.
- **Rationale**: SC-002 requires 100% coverage with correct names/defaults; a guide that drifts from code
  is worse than none.
- **Sources** (verified):
  - `server/src/main/resources/application.yml`: `server.address` = `127.0.0.1`, `server.port` = `8077`
    (override `SERVER_ADDRESS`/`SERVER_PORT`); `kontinuance.store` = `${user.home}/.kontinuance/runs`
    (override `KONTINUANCE_STORE`); `kontinuance.stream.poll-interval-ms` = `1000`,
    `kontinuance.stream.snapshot-limit` = `50`; `management.endpoints.web.exposure.include` = `health`.
  - `ConfigController` / `RunTrigger`: `kontinuance.config.descriptor` default `kontinuance.yml`.
  - `CoverageController`: `kontinuance.coverage.report` default `build/reports/kover/report.xml`.
  - Engine `EnvSecretSource`: pipeline `secrets:` are resolved from process environment variables.
  - `web/vite.config.ts`: dev proxy target `KONTINUANCE_API` (default `http://localhost:8077`); proxies
    `/api` and `/ws` (`ws: true`); adapter-static with `fallback: 'index.html'` → SPA in `web/build/`.
  - `server WebSocketConfig`: the live WebSocket path is `/ws/runs` (SSE is `/api/runs/stream`).
- **Alternatives considered**: documenting a curated subset — rejected; an operator hitting an
  undocumented setting must not have to read source (fails SC-002).

## Decision: provide both an nginx and a Caddy reverse-proxy example

- **Decision**: Ship `docs/examples/nginx.conf` and `docs/examples/Caddyfile`, each serving the static SPA
  and proxying `/api` and `/ws/*` (with the WebSocket upgrade) to the server on one origin.
- **Rationale**: Same-origin is required (the client calls same-origin `/api` + `/ws`); nginx and Caddy
  cover the two most common choices, and the WebSocket upgrade for `/ws/runs` is the easy-to-miss detail.
- **Alternatives considered**: one example only — rejected as less useful; Kubernetes/Compose manifests —
  out of scope per spec.

## Decision: the example descriptor demonstrates the gate-in-its-own-stage pattern and must parse

- **Decision**: `docs/examples/kontinuance.yml` = `pipeline:` with stages **build → test → approval (own
  stage) → deploy**, using only keys the strict parser accepts.
- **Rationale**: FR-005/FR-008 and the durable-approval authoring rule (a resumed run re-enters the paused
  stage from the top, so the gate must be alone in its stage to avoid repeating prior work).
- **Parser rules** (from `engine/.../descriptor/PipelineDescriptor.kt`): top-level `pipeline:` only;
  pipeline keys `{name, concurrency, stages}`; stage keys `{name, steps}`; step keys `{name, timeout,
  when, secrets, workingDir}` + exactly one of `{run, gradle, docker, npm, approval}`; `when:` is the
  condition key; unknown/duplicate keys are rejected.
- **Alternatives considered**: a minimal one-stage sample — rejected; it would not demonstrate the gate
  placement that the guidance is about.

## Decision: verification is a docs review + a real-parser check of the example

- **Decision**: The example descriptor is loaded through the actual `PipelineDescriptor.parse` to prove
  validity (see `quickstart.md`); the guide is reviewed against `contracts/config-surface.md`.
- **Rationale**: There is no runtime code in this feature to exercise; the only executable correctness
  claim is "the example parses", and that is checked against the real parser, not a copy of the rules.
- **Alternatives considered**: eyeballing the YAML — rejected; the parser is strict and easy to violate.
