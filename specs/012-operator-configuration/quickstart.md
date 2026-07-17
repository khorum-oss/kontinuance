# Quickstart / Verification: Operator Configuration & Deployment Guide

How to confirm this feature is correct. There is no runtime code to run; verification is (1) a real-parser
check of the example descriptor and (2) a review of the guide against the source-of-truth contracts.

## Prerequisites

- Repository checked out; JDK 21 + Gradle available (for the parser check).

## 1. The example descriptor parses (FR-005, SC-003)

The example must be accepted by the real strict parser exactly as the server would accept it. Verify by
loading `docs/examples/kontinuance.yml` through `PipelineDescriptor.parse` — e.g. a temporary engine test
or a scratch main that reads the file and calls the parser. Expected: it returns a `Pipeline` with stages
`build`, `test`, an approval stage, and `deploy`, with **no** `DescriptorException`.

Also confirm the gate placement: the approval step is the only step in its stage, positioned after
`test` and before `deploy` (`contracts/descriptor-validity.md`).

## 2. The guide covers 100% of the configuration surface (FR-001, SC-002)

Check `docs/running.md` against `contracts/config-surface.md`: every row (server settings, web
`KONTINUANCE_API`, build output, secrets) appears with its name and default. No setting an operator must
otherwise discover from source is missing.

## 3. The proxy examples serve one origin with the WebSocket upgrade (FR-003, FR-004)

Review `docs/examples/nginx.conf` and `docs/examples/Caddyfile`: static assets from `web/build/` with an
`index.html` fallback, `/api` and `/api/runs/stream` proxied to the server (SSE buffering disabled), and
`/ws/runs` proxied **with** the `Upgrade`/`Connection` headers. Optionally apply one proxy locally, build
the web app (`pnpm --dir web build`), run the server, and load the UI to see the runs list populate.

## 4. Limitations are stated (FR-006, FR-007, SC-004)

Confirm `docs/running.md` states, explicitly: no authentication on trigger/approve/reject (+ loopback
default + front with an authenticating proxy); only gate-paused runs are restart-durable; the durable
gate assumes a single instance sharing the store.

## 5. Constraints (Principle V, FR-010, SC-005)

- No real secret values anywhere — placeholder env var names only.
- No external design link.
- No new dependency; `gradle/verification-metadata.xml` unchanged.
