# Implementation Plan: Web UI (Mission-Control Dashboard)

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` (009 numbers the specs dir only) | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

## Summary

A **SvelteKit + Storybook** dashboard in a new `web/` app that observes the engine over the 007/008 read
API + live stream. Component-library-first: shared elements and each screen are Svelte components with
Storybook stories driven by typed fixtures. Runs / run-detail / live are wired to the real API; pipeline,
deploy, coverage (Kover), and config are wired to **new typed stub endpoints** on the Spring server so
every screen is data-connected with a stable contract. Delivered in incremental commits toward one UI PR.

## Technical Context

**Frontend**: SvelteKit 2 + Svelte 5 (runes) + TypeScript + Vite; Storybook 8 (`@storybook/sveltekit`);
Vitest for component tests. Package manager: pnpm. Theme: near-black canvas `#070b10`, teal accent
`#5eead4`, Space Grotesk + JetBrains Mono, CSS keyframe animations (pulse/flow/blink).

**Backend (stubs)**: new suspend `@RestController`s in `:server` returning typed JSON for
pipeline/deploy/coverage/config; coverage stub modeled on **Kover** (line/branch/module), optionally
reading `build/reports/kover/report.xml`. No change to the existing `/api` contract; verification stays
enabled (no new server deps needed — pure controllers over Kotlin data classes serialized as raw JSON via
the existing `JsonView`/serialization approach).

**API surface consumed**: `GET /api/health`, `GET /api/runs?limit=N`, `GET /api/runs/{id}`, SSE
`GET /api/runs/stream`, WebSocket `/ws/runs`; NEW `GET /api/runs/{id}/pipeline`, `GET /api/deploy`,
`GET /api/coverage`, `GET /api/config`.

**Testing**: Storybook stories per component (visual isolation); Vitest unit tests for the API client and
key components; server stub controllers covered by `@SpringBootTest` + `WebTestClient` like the read API.

**Constraints**: no external design-source reference anywhere (FR-010); dev-proxy to the server for
`/api` + streams; local build uses pnpm (network to npm registry is available); CI wiring for the web app
is a follow-up (this feature lands the app + stories building locally).

## Constitution Check

- **I. Stable Public Contract** — PASS. New endpoints are additive under `/api`; existing routes/shape
  unchanged (a server test pins them).
- **II. Test-First & Integration-Verified** — PASS. Stub controllers get `@SpringBootTest` + real HTTP
  round-trip; components get Storybook stories + Vitest.
- **III. Quality Gates** — PASS. Server stays under detekt/Kover; the web app adds lint/type-check.
- **IV. Code Generation** — N/A.
- **V. Supply-Chain Integrity** — PASS. Server stubs add no new JVM deps, so `verification-metadata.xml`
  is untouched and verification stays enabled. Frontend deps live in `web/` (npm ecosystem, separate).

**Result: PASS.**

## Project Structure

```text
web/                                   # NEW SvelteKit app
├── package.json, svelte.config.js, vite.config.ts, tsconfig.json
├── .storybook/                        # Storybook config
└── src/
    ├── app.css                        # theme tokens (colors, fonts, keyframes)
    ├── lib/
    │   ├── theme/tokens.ts            # typed design tokens (colors, tool colors, status map)
    │   ├── api/                       # client.ts (fetch), live.ts (SSE/WS), types.ts
    │   ├── fixtures/                  # typed example data for stories + stub screens
    │   ├── components/                # StatusDot, ProgressBar, ToolBadge, RunRow, StageCard,
    │   │                              #   CoverageBar, LogLine, Sidebar, Topbar, … (+ *.stories.ts)
    │   └── screens/                   # Login, Runs, Pipeline, RunDetail, Deploy, Coverage, Config
    └── routes/                        # SvelteKit routes mapping URLs → screens

server/src/main/kotlin/.../server/stub/   # NEW PipelineController, DeployController,
                                           #   CoverageController, ConfigController (typed, fixture JSON)
specs/009-web-ui/contracts/stub-api.md     # the four stub endpoint contracts
```

**Structure Decision**: a standalone `web/` app (SvelteKit) alongside the Gradle modules; the server gains
four additive stub controllers. The engine and existing `/api` are untouched.

## Delivery Increments (incremental commits, one PR)

1. **Scaffold + theme + shell + Runs (wired)** — SvelteKit+Storybook, tokens, Sidebar/Topbar/Login shell,
   API client, Runs screen on the real API, core component stories. *(this increment)*
2. **Live** — SSE/WS client, live runs list + run-detail updates.
3. **Run detail** — log stream view + coverage sidebar.
4. **Stub endpoints** — server PipelineController/DeployController/CoverageController/ConfigController + tests.
5. **Pipeline / Deploy / Coverage / Config screens** — wired to the stubs, with stories.
6. **Polish** — empty/error states, README, quickstart, lint/type-check green.

## Complexity Tracking

No violations. The stubs are deliberately typed-contract-first so the forward-looking screens are
data-connected now and swap to real sources later without UI change.
