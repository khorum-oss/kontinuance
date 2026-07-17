# Kontinuance Web UI

A SvelteKit + Storybook "mission control" dashboard for the Kontinuance CI/CD engine. It observes the
engine over the server read API (`/api/health`, `/api/runs`, `/api/runs/{id}`) plus live updates over SSE
(`/api/runs/stream`) and WebSocket (`/ws/runs`).

Component-library-first: every shared element and screen is a Svelte component with a Storybook story
driven by typed fixtures, so each is reviewable in isolation before it is assembled into a screen.

## Develop

```bash
pnpm install
pnpm dev            # http://localhost:5173  (proxies /api + /ws to the server)
pnpm storybook      # http://localhost:6006  (component library)
```

Point the dev proxy at a running server (default `http://localhost:8077`):

```bash
# start the server (from the repo root):  ./gradlew :server:run
KONTINUANCE_API=http://localhost:8077 pnpm dev
```

## Scripts

| Script                 | What it does                                     |
| ---------------------- | ------------------------------------------------ |
| `pnpm dev`             | Vite dev server (SPA) with `/api` + `/ws` proxy  |
| `pnpm build`           | Static SPA build (adapter-static) → `build/`     |
| `pnpm check`           | `svelte-check` type/lint of app + stories        |
| `pnpm storybook`       | Storybook dev server                             |
| `pnpm build-storybook` | Static Storybook → `storybook-static/`           |

## Layout

```
src/
  app.css                 theme tokens (canvas/teal, Space Grotesk + JetBrains Mono, keyframes)
  lib/
    theme/tokens.ts        typed tokens (status→color, tool→color)
    api/                   client.ts (fetch), present.ts (run view helpers), types.ts
    fixtures/              typed example data for stories
    components/            StatusDot, ProgressBar, ToolBadge, RunRow, Sidebar, Topbar, Login (+ *.stories.svelte)
    screens/               Runs (wired), Placeholder
  routes/                  shell layout + / (Runs) + /runs/[id] + pipeline/deploy/coverage/config
```

## Data sources

- **Runs / run detail / live** read the **real** server API.
- **Pipeline / Deploy / Coverage (Kover) / Config** read **stub endpoints** on the server (stable typed
  contracts, fixture data now) — see `../specs/009-web-ui/contracts/stub-api.md`. They swap to real
  sources later without UI change.
