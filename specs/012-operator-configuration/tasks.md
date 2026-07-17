# Tasks: Operator Configuration & Deployment Guide

**Feature**: 012-operator-configuration | **Branch**: `claude/operator-configuration`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Docs-and-examples feature — **no engine/server/web code changes**. Every configuration value traces to
[contracts/config-surface.md](./contracts/config-surface.md); the example descriptor must satisfy
[contracts/descriptor-validity.md](./contracts/descriptor-validity.md).

**MVP** = User Story 1 (stand up the service + UI on one origin with the config documented).

---

## Phase 1: Setup

- [x] T001 [P] Create the `docs/examples/` directory for the copyable artifacts

## Phase 2: Foundational (blocking prerequisites)

- [x] T002 Re-verify [specs/012-operator-configuration/contracts/config-surface.md](./contracts/config-surface.md) against the live sources (`server/src/main/resources/application.yml`, `ConfigController`/`CoverageController`/`RunTrigger` defaults, `web/vite.config.ts`, `WebSocketConfig`) so every value the guide will cite is current — blocks all guide-writing tasks

---

## Phase 3: User Story 1 — Stand up service + UI on one origin (Priority: P1) 🎯 MVP

**Goal**: An operator can set every config value and serve the SPA + API on one origin.
**Independent test**: Following only `docs/running.md` + a proxy example, start the server, serve
`web/build/`, load the UI, and see the runs list populate — no source reading.

- [x] T003 [P] [US1] Write `docs/examples/nginx.conf`: serve static `web/build/` with `index.html` fallback; proxy `/api/**` and `/api/runs/stream` to the server with `proxy_buffering off` for SSE; proxy `/ws/runs` with `Upgrade`/`Connection` upgrade headers; single origin
- [x] T004 [P] [US1] Write `docs/examples/Caddyfile`: `file_server` over `web/build/` with SPA fallback; `reverse_proxy` for `/api/*` and `/ws/runs` to the server (Caddy handles the WebSocket upgrade); single origin
- [x] T005 [US1] Write the configuration-surface sections of `docs/running.md` from [contracts/config-surface.md](./contracts/config-surface.md): server settings table (bind/port, store, descriptor, coverage report, stream tuning, actuator), secrets via environment (`EnvSecretSource`, placeholder names), and the web app's `KONTINUANCE_API` + `web/build/` output — satisfies FR-001, FR-002
- [x] T006 [US1] Add the same-origin serving model + a "start here" walkthrough to `docs/running.md` (build server + web, set config, run behind the proxy, load the UI), pointing at the nginx/Caddy examples — satisfies FR-003, FR-004

**Checkpoint**: US1 is independently complete — the service runs and is reachable via the guide.

---

## Phase 4: User Story 2 — Author a valid gated pipeline from an example (Priority: P2)

**Goal**: A copyable, parser-valid descriptor with the gate in its own stage before deploy.
**Independent test**: The example loads through the real parser and can be a triggered run's descriptor
unedited.

- [x] T007 [P] [US2] Write `docs/examples/kontinuance.yml`: `pipeline:` with stages **build → test → approval (own stage) → deploy**, using only keys the strict parser accepts (`when:`, one of `run/gradle/docker/npm/approval` per step) per [contracts/descriptor-validity.md](./contracts/descriptor-validity.md) — satisfies FR-005
- [x] T008 [US2] Verify `docs/examples/kontinuance.yml` parses via the real `PipelineDescriptor.parse` (scratch engine test or main per [quickstart.md](./quickstart.md) §1); confirm the 4 stages and that the approval step is alone in its stage between `test` and `deploy`; remove any scratch code afterward — satisfies SC-003
- [x] T009 [US2] Add the descriptor-authoring section to `docs/running.md` (top-level `pipeline:`, `when:` not `condition:`, exactly one step type per step, and the gate-in-its-own-stage rule so resume repeats no prior work), referencing the example — satisfies FR-008

**Checkpoint**: An operator can author a correct gated pipeline from the example + guidance.

---

## Phase 5: User Story 3 — Understand security & durability limits (Priority: P3)

**Goal**: The guide states the operational limitations an operator must know before exposing the service.
**Independent test**: The limitations and troubleshooting sections state each caveat with the operator
action it implies.

- [x] T010 [US3] Add the limitations section to `docs/running.md`: no authentication on trigger/approve/reject (+ loopback default + front with an authenticating proxy before exposure); only gate-paused runs are restart-durable while actively-running runs are not; the durable gate assumes a single instance sharing one store — satisfies FR-006, FR-007
- [x] T011 [US3] Add a troubleshooting/fallback section to `docs/running.md`: missing/invalid descriptor → trigger rejected + config screen shows fixtures; missing coverage report → coverage falls back to fixtures — satisfies FR-009

**Checkpoint**: All three stories complete; the guide is decision-ready for a real deployment.

---

## Phase 6: Polish & Cross-Cutting

- [x] T012 Review `docs/running.md` against [contracts/config-surface.md](./contracts/config-surface.md): 100% of settings present with name + default (SC-002); fix any gaps
- [x] T013 [P] Verify no real secret values and no external design links anywhere in `docs/running.md`, `docs/examples/kontinuance.yml`, `docs/examples/nginx.conf`, `docs/examples/Caddyfile` — placeholder env names only (SC-005, FR-010)
- [x] T014 Ensure each new file ends with a trailing newline and is cross-referenced from `docs/` where natural (e.g. a pointer from `docs/overview.md` or `docs/roadmap.md`); confirm FR-011 (no engine/server/web code changed) via `git status`

---

## Dependencies

- **Setup (T001)** → before any `docs/examples/*` file.
- **Foundational (T002)** → before every guide-writing task (T005, T006, T009, T010, T011) and the example (T007).
- **User stories**: independent in intent, but T005/T006/T009/T010/T011 all edit the single file
  `docs/running.md`, so run them sequentially in that file; the example files are parallelizable.
- **Polish (T012–T014)** → after all story tasks.

## Parallel Opportunities

- `[P]` files are independent and can be written in parallel: **T003** (`nginx.conf`), **T004**
  (`Caddyfile`), **T007** (`kontinuance.yml`). T001 setup is also `[P]`. T013 is `[P]` (read-only review).
- All `docs/running.md` tasks are **not** `[P]` with each other (same file).

## Implementation Strategy

- **MVP first**: complete Phase 1–3 (US1) → the service is runnable and documented. Ship-able alone.
- **Increment**: add US2 (example + authoring), then US3 (limitations), then Polish.
- **Verification**: T008 (real-parser check) and T012 (config-surface coverage) are the two objective
  gates; T013/T014 are the constraint checks (no secrets, no design links, no code changes).
