# Tasks: Web UI (Mission-Control Dashboard)

**Input**: specs/009-web-ui/ (spec.md, plan.md, contracts/stub-api.md)

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` · **Delivery**: incremental commits, one PR.

**Format**: `[ID] [P?] [Story] Description` — [P] parallelizable, [US1–US4] maps to spec user stories.

---

## Phase 1: Setup — scaffold + theme (Increment 1)

- [x] T001 Scaffold a SvelteKit + TS + Vite app in `web/` (Svelte 5, pnpm) with a dev proxy to the server for `/api` + `/ws`.
- [x] T002 Add Storybook (`@storybook/sveltekit`) + Vitest; `pnpm storybook` and `pnpm test` run.
- [x] T003 [P] Theme: `src/app.css` (canvas/teal tokens, Space Grotesk + JetBrains Mono, keyframes) + `src/lib/theme/tokens.ts` (typed colors, tool colors, status→color map).

## Phase 2: Foundational — API client + fixtures

- [x] T004 `src/lib/api/types.ts` — typed `RunSummary`/`RunDetail` + stub types (Pipeline, Deploy, Coverage, Config) mirroring contracts/stub-api.md.
- [x] T005 `src/lib/api/client.ts` — fetch client: `health()`, `listRuns(limit)`, `getRun(id)`, `getPipeline(id)`, `getDeploy()`, `getCoverage()`, `getConfig()` with error typing.
- [x] T006 [P] `src/lib/fixtures/*` — typed example data for every entity (runs, pipeline stages/tasks, deploy, coverage, config) for stories + stubs.

## Phase 3: US1 — runs list + open a run (Priority: P1) 🎯 MVP

- [x] T007 [P] [US1] Shared components + stories: `StatusDot`, `ProgressBar`, `ToolBadge`, `RunRow`.
- [x] T008 [P] [US4] Shell components + stories: `Sidebar`, `Topbar`, `Login` (presentational entry).
- [x] T009 [US1] `screens/Runs.svelte` — newest-first runs table wired to `listRuns`, with loading/empty/error states + story.
- [x] T010 [US1] Routes: shell layout + `/` (Runs) + `/runs/[id]` (detail placeholder); sidebar nav highlights active screen.

## Phase 4: US2 — live updates (Priority: P1)

- [x] T011 [US2] `src/lib/api/live.ts` — SSE (`/api/runs/stream`) + WebSocket (`/ws/runs`) client exposing a Svelte store of run updates, with reconnect + degraded state.
- [x] T012 [US2] Wire the runs list (and open detail) to the live store: new/updated runs appear within ~3s without reload; degraded indicator on disconnect.

## Phase 5: US2/US1 — run detail (Priority: P1)

- [ ] T013 [P] [US1] `LogLine` + `CoverageBar` components + stories.
- [ ] T014 [US1] `screens/RunDetail.svelte` — run header + streamed log view + coverage sidebar, wired to `getRun(id)` + live stream.

## Phase 6: Stub endpoints (server) + forward-looking screens (Priority: P2)

- [ ] T015 [P] Server: `stub/PipelineController.kt` `GET /api/runs/{id}/pipeline` — typed stages/tasks (fixture).
- [ ] T016 [P] Server: `stub/DeployController.kt` `GET /api/deploy` — nodes + artifact manifest + env health (fixture).
- [ ] T017 [P] Server: `stub/CoverageController.kt` `GET /api/coverage` — Kover line/branch/module (read `build/reports/kover/report.xml` when present, else fixture).
- [ ] T018 [P] Server: `stub/ConfigController.kt` `GET /api/config` — resolved config text + plan summary (fixture).
- [ ] T019 Server tests: `@SpringBootTest` + `WebTestClient` for the four stub endpoints; confirm `/api` contract + verification unchanged.
- [ ] T020 [P] [US3] `StageCard` + stage-flow + telemetry-feed components + stories.
- [ ] T021 [US3] `screens/Pipeline.svelte` wired to `getPipeline(id)` (stage/task flow + dependency tracing + telemetry).
- [ ] T022 [US3] `screens/Deploy.svelte` wired to `getDeploy()` (promotion nodes + artifact manifest + env health).
- [ ] T023 [US3] `screens/Coverage.svelte` wired to `getCoverage()` (Kover table + module drill-down).
- [ ] T024 [US3] `screens/Config.svelte` wired to `getConfig()` (resolved config view + plan summary).

## Phase 7: Polish

- [ ] T025 [P] Empty/loading/error states audited across all seven screens.
- [ ] T026 [P] `web/README.md` + `specs/009-web-ui/quickstart.md` (run dev, storybook, point at a server).
- [ ] T027 Lint + type-check + Vitest + `pnpm build` + `pnpm build-storybook` green; roadmap updated (009 built).

---

## Dependencies

- Setup (P1) → Foundational (P2) → US1 (MVP) → US2 → detail → stubs+US3 → polish.
- Stub controllers (T015–T019) are independent of the frontend and can land in parallel with US1/US2.
- No engine change; no new JVM dependency (verification-metadata untouched).

## Notes

- Wired screens (Runs/Detail/live) read only the real API; forward-looking screens read only their stub
  endpoints — no data baked into wired screens (SC-005).
- No external design-source location anywhere in code or docs (FR-010/SC-007).
