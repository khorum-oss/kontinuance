# Running & Configuring Kontinuance

This guide is for operators standing up Kontinuance in their own environment. It covers the full runtime
configuration surface, how to serve the web UI and API on one origin, how to author a pipeline, and the
security and durability limitations to understand **before** exposing the service.

Kontinuance runs as two pieces:

- the **server** — a Spring Boot service exposing the read API, the manual-trigger/approval endpoints,
  and the live run stream (SSE + WebSocket);
- the **web UI** — a static single-page app (built with `adapter-static`, `ssr=false`) that talks to the
  server over the same origin.

---

## Prerequisites

- The built server (JDK 21 runtime).
- The built web UI: `pnpm --dir web build` → static assets in `web/build/` (SPA with an `index.html`
  routing fallback).
- A reverse proxy (nginx or Caddy) to serve the UI and the API on one origin — see
  [`examples/nginx.conf`](./examples/nginx.conf) and [`examples/Caddyfile`](./examples/Caddyfile).

## Quick start

1. **Configure** the server (see [Configuration](#configuration)). At minimum decide the run-store
   location and the path to your pipeline descriptor.
2. **Start the server.** It binds `127.0.0.1:8077` by default (loopback — not reachable from the network
   until you put a proxy in front).
3. **Build the UI** (`pnpm --dir web build`) and point your reverse proxy's static root at `web/build/`.
4. **Wire the proxy** so `/api/**` and `/ws/runs` reach the server on the same origin as the UI (copy one
   of the [examples](#serving-the-ui-and-api-on-one-origin)).
5. **Load the UI** in a browser. The runs list populates from the live API; trigger a run with **RUN
   PIPELINE**; approve a gated run from its detail view.

---

## Configuration

All server settings use Spring's relaxed binding — set them as JVM properties (`--kontinuance.store=…`),
`application.yml` overrides, or the environment variables shown below.

### Server

| Setting | Environment variable | Default | What it does |
|---|---|---|---|
| `server.address` | `SERVER_ADDRESS` | `127.0.0.1` | Bind address. Loopback by default — see [Authentication](#authentication). |
| `server.port` | `SERVER_PORT` | `8077` | Listen port. |
| `kontinuance.auth.username` | `KONTINUANCE_AUTH_USERNAME` | _(unset)_ | Operator login name. Set **with** the password to enforce authentication; see [Authentication](#authentication). |
| `kontinuance.auth.password` | `KONTINUANCE_AUTH_PASSWORD` | _(unset)_ | Operator password. Never commit the value. Both must be set to enforce auth; unset ⇒ open mode + startup warning. |
| `kontinuance.store` | `KONTINUANCE_STORE` | `~/.kontinuance/runs` | Directory of run history (the file-backed run store). |
| `kontinuance.config.descriptor` | `KONTINUANCE_CONFIG_DESCRIPTOR` | `kontinuance.yml` | Pipeline descriptor loaded for `/api/config` and for triggered/resumed runs. |
| `kontinuance.coverage.report` | `KONTINUANCE_COVERAGE_REPORT` | `build/reports/kover/report.xml` | Kover XML surfaced by the coverage screen. |
| `kontinuance.stream.poll-interval-ms` | `KONTINUANCE_STREAM_POLL_INTERVAL_MS` | `1000` | How often the live stream re-reads the store for new runs. |
| `kontinuance.stream.snapshot-limit` | `KONTINUANCE_STREAM_SNAPSHOT_LIMIT` | `50` | Newest-first snapshot size sent when a client connects to the stream. |
| `management.endpoints.web.exposure.include` | — | `health` | Actuator exposes only `/actuator/health`. |

Paths are resolved relative to the server's working directory unless absolute.

### Secrets

Pipeline steps reference secrets by name (`secrets: ["DEPLOY_TOKEN"]`). The engine resolves each name
from the **server process's environment variables** and masks the value in streamed logs. Supply them as
environment variables (or an untracked env file) — **never commit secret values**. A run fails fast if a
referenced secret is not set.

### Web UI

| Setting | Where | Default | What it does |
|---|---|---|---|
| `KONTINUANCE_API` | `web/vite.config.ts` (dev server only) | `http://localhost:8077` | Target the Vite dev proxy forwards `/api` and `/ws` to during `pnpm --dir web dev`. |
| build output | `adapter-static` | `web/build/` | Static assets to serve in production (SPA, `index.html` fallback). |

In production the UI does not read `KONTINUANCE_API` — it calls the **same origin** it was served from, so
the reverse proxy is what connects it to the server.

---

## Serving the UI and API on one origin

The UI calls same-origin `/api/**`, `/api/runs/stream` (SSE), and `/ws/runs` (WebSocket), so the browser
must reach the server through the **same origin** that served the UI. A reverse proxy does this:

- serve the static SPA from `web/build/` with an `index.html` fallback for client routing;
- proxy `/api/**` to the server;
- proxy `/api/runs/stream` with response buffering **disabled** so events stream immediately;
- proxy `/ws/runs` **with** the WebSocket upgrade headers.

Copy one of the ready examples and adapt the hostname, static root, and upstream address:

- nginx → [`examples/nginx.conf`](./examples/nginx.conf)
- Caddy → [`examples/Caddyfile`](./examples/Caddyfile)

---

## Authoring pipelines

The server runs the pipeline defined by its configured descriptor
(`kontinuance.config.descriptor`). See the annotated [`examples/kontinuance.yml`](./examples/kontinuance.yml)
for a complete gated flow. The descriptor parser is **strict** — unknown or duplicate keys are rejected —
so keep to these rules:

- The document has a single top-level key, `pipeline:`.
- `pipeline` accepts `name` (required), `concurrency`, and `stages`.
- Each `stage` has a `name` and `steps`.
- Each `step` has a `name`, optionally `timeout` / `when` / `secrets` / `workingDir`, and **exactly one**
  step type: `run`, `gradle`, `docker`, `npm`, `approval`, or `git` (a source checkout into the run's
  shared workspace).
- The condition key is **`when:`** (not `condition:`).

### Manual-approval gates

An `approval:` step pauses the run until an operator approves or rejects it from the run's detail view.
Its value is the prompt shown to the approver:

```yaml
- name: "promote-to-prod"
  approval: "Promote this build to production?"
```

**Put the gate in its own stage**, positioned after build/test and before deploy (as in the example).
When an approved run resumes, the engine re-enters the paused stage from the top — so a gate that shares a
stage with other steps would re-run those steps on resume. A gate alone in its stage repeats no prior
work. Rejecting a gated run ends it **Cancelled** (a deliberate stop, not a failure).

---

## Limitations

Understand these before putting Kontinuance on a network.

### Authentication

Authentication is **opt-in** and off by default. Set **both** `KONTINUANCE_AUTH_USERNAME` and
`KONTINUANCE_AUTH_PASSWORD` (never commit the values) to enforce a login gate on the API: every endpoint —
the runs read API, `POST /api/runs/trigger`, `POST /api/runs/{id}/approve|reject`, the SSE stream, and the
WebSocket — then requires a valid session. Public paths stay open in both modes: the auth endpoints
(`/api/auth/login`, `/api/auth/me`, `/api/auth/logout`), `/api/health`, and `/actuator/health`.

- **Sign in** with `POST /api/auth/login` (`{"username","password"}`). On success the server sets an
  HttpOnly `KSESSION` cookie; subsequent calls carry it. `GET /api/auth/me` reports the signed-in user and
  whether auth is required; `POST /api/auth/logout` ends the session.
- Credentials are compared in constant time; a wrong username and a wrong password are indistinguishable.
- **When the two variables are unset the server runs open** (unauthenticated) and logs a warning at startup.
  The loopback bind (`127.0.0.1`) remains the safe default for open mode — the service is not
  network-reachable until you change `SERVER_ADDRESS` or front it with a proxy.

Sessions are in-memory and single-instance: they do not survive a restart and are not shared across
instances (consistent with [durability](#durability-only-paused-runs-survive-a-restart)). For a stronger
posture you can still terminate TLS and add SSO/forward-auth or mTLS at the reverse proxy.

> **Web UI note**: the SPA login screen does not yet call `/api/auth/login` — wiring the browser flow
> (real sign-in, the signed-in name in the sidebar, EXIT → project view) is the remaining follow-up. Enable
> server auth today via the API/`curl` or a proxy; with auth enforced, use the UI only once that wiring
> lands (otherwise the SPA's calls are rejected).

### Durability: only paused runs survive a restart

Run execution is in-process. A run **paused at an approval gate** is durable — it is persisted (with its
completed stages) in the run store, and approve/reject resolve it from that stored state, so it survives a
server restart. A run that is **actively executing** when the process stops is **not** recovered — it will
need re-triggering. Durability covers the approval wait, not a mid-build crash.

### Single instance

The durable approval gate assumes a **single server instance sharing one run store**. There is no leader
election or double-run guard, so do not run multiple instances against the same store without a proxy that
routes all writes to one instance.

---

## Troubleshooting

- **A trigger is rejected / the Config screen shows placeholder data.** The configured descriptor
  (`kontinuance.config.descriptor`) is missing or invalid. `POST /api/runs/trigger` returns `400` with the
  reason, and `/api/config` falls back to fixture data. Check the path and validate the YAML against the
  [authoring rules](#authoring-pipelines).
- **The Coverage screen shows placeholder numbers.** No Kover report was found at
  `kontinuance.coverage.report`. Generate it (`./gradlew koverXmlReport`) and point the setting at the XML.
- **The runs list does not update live / the WebSocket fails.** The proxy is not forwarding
  `/api/runs/stream` (SSE) or `/ws/runs` (WebSocket upgrade) correctly. Compare against the
  [proxy examples](#serving-the-ui-and-api-on-one-origin) — SSE needs buffering off, `/ws/runs` needs the
  upgrade headers.
- **The UI loads but every API call fails.** The UI and API are not on the same origin. Serve both through
  one reverse proxy (see above).
