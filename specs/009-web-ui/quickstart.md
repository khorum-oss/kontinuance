# Quickstart: Web UI (Mission-Control Dashboard)

## Run it

```bash
cd web
pnpm install
pnpm dev            # http://localhost:5173  (proxies /api + /ws to the server)
pnpm storybook      # http://localhost:6006  (component library)
```

Against a running server (from the repo root):

```bash
./gradlew :server:run                       # serves the read API + stubs on :8077
KONTINUANCE_API=http://localhost:8077 pnpm --dir web dev
```

## Validate (US1–US4)

- **Runs** (`/`) — sign in → pick a repo → the runs list renders newest-first from `GET /api/runs`, with
  loading / empty / error states; a new run pushed over `GET /api/runs/stream` (SSE) appears without a
  reload; the degraded banner shows on stream disconnect.
- **Run detail** (`/runs/{id}`) — run header + log timeline + Kover coverage sidebar from
  `GET /api/runs/{id}` (+ `GET /api/coverage`); unknown id → not-found.
- **Pipeline / Deploy / Coverage / Config** — each renders from its stub endpoint
  (`/api/runs/{id}/pipeline`, `/api/deploy`, `/api/coverage`, `/api/config`); Pipeline traces task
  dependencies on hover; Coverage drills into a module.

## Automated verification

```bash
cd web
pnpm check          # svelte-check (type/lint) of app + stories
pnpm test:unit      # Vitest node unit tests (tokens, presentation, client, live store)
pnpm test:e2e       # Playwright E2E: the full app with /api/* mocked at the browser layer
pnpm test:stories   # (optional) Storybook stories run as browser component tests
pnpm build && pnpm build-storybook
```

CI runs `pnpm check` + `pnpm test:unit` + Playwright E2E in the `web-tests` job (`.github/workflows/pr-main.yml`).

## Notes

- Runs / run-detail / live read the **real** API; pipeline/deploy/coverage/config read **additive stub
  endpoints** on `:server` (fixture data now, real sources later) — see [contracts/stub-api.md](./contracts/stub-api.md).
- Live **step-log** streaming and real pipeline/deploy/coverage sources are follow-ups; the engine records
  run metadata, not step logs, today.
