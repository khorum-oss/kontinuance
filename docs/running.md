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
| `kontinuance.auth.password` | `KONTINUANCE_AUTH_PASSWORD` | _(unset)_ | Operator password. Never commit the value. Both must be set to enforce auth; both unset ⇒ open mode + startup warning; **only one set ⇒ startup failure**. |
| `kontinuance.auth.required` | `KONTINUANCE_AUTH_REQUIRED` | `false` | Assert that authentication is mandatory: missing credentials become a startup failure instead of open mode. Set it on any deployment that must never run open. |
| `kontinuance.store` | `KONTINUANCE_STORE` | `~/.kontinuance/runs` | State directory holding run history and its per-run output. |
| `kontinuance.store.backend` | `KONTINUANCE_STORE_BACKEND` | `sqlite` | Which store backs the history: `sqlite` keeps runs and output in one embedded database, `<store>/kontinuance.db`; `file` keeps the original file-per-run JSON layout. An unrecognised name is a **startup failure** rather than a silent fallback. See [Run history](#run-history). |
| `kontinuance.config.descriptor` | `KONTINUANCE_CONFIG_DESCRIPTOR` | `kontinuance.yml` | Pipeline descriptor loaded for `/api/config` and for triggered/resumed runs. |
| `kontinuance.coverage.report` | `KONTINUANCE_COVERAGE_REPORT` | `build/reports/kover/report.xml` | Kover XML surfaced by the coverage screen. |
| `kontinuance.stream.poll-interval-ms` | `KONTINUANCE_STREAM_POLL_INTERVAL_MS` | `1000` | How often the live stream re-reads the store for new runs. |
| `kontinuance.stream.snapshot-limit` | `KONTINUANCE_STREAM_SNAPSHOT_LIMIT` | `50` | Newest-first snapshot size sent when a client connects to the stream. |
| `kontinuance.github.config` | `KONTINUANCE_GITHUB_CONFIG` | `~/.kontinuance/github-source.yaml` | Event-source config. Written by the Source screen's connect form and read at startup; the same file the `kontinuance-ci` CLI takes as its argument. |
| `kontinuance.github.cursors` | `KONTINUANCE_GITHUB_CURSORS` | `~/.kontinuance/github-cursors.properties` | Poll cursors — the last commit processed per PR/branch. |
| `kontinuance.github.heartbeat` | `KONTINUANCE_GITHUB_HEARTBEAT` | `~/.kontinuance/github-heartbeat.properties` | Liveness signal written after each successful poll (036). |
| `kontinuance.github.token-file` | `KONTINUANCE_GITHUB_TOKEN_FILE` | `~/.kontinuance/github-token` | Where a token supplied through the UI is stored (owner-only permissions). Never returned by the API. An environment variable named by the config's `tokenEnv` takes precedence. |
| `kontinuance.github.autostart` | `KONTINUANCE_GITHUB_AUTOSTART` | `true` | Resume a stored event source at startup. Set `false` to leave it stopped until someone starts it from the UI. |
| `management.endpoints.web.exposure.include` | — | `health` | Actuator exposes only `/actuator/health`. |

Paths are resolved relative to the server's working directory unless absolute.

### GitHub event source

The server can host the GitHub poll loop itself, so watching a repository takes one process rather than a
server plus a separate `kontinuance-ci` CLI. Connect a repository from the **Source** screen — see
[Connecting GitHub](./getting-started.md#connecting-github) for the walkthrough.

Two things are worth knowing before you connect:

- **The write endpoints require operator authentication.** `POST`/`DELETE /api/source` accept an access
  token and start outbound work under it, so an unauthenticated server refuses them with `409` and the
  Source screen explains why instead of showing the form. Reads are unaffected.
- **A supplied token is stored at rest**, in `kontinuance.github.token-file`, created with owner-only
  permissions and never returned by any read. To avoid that, leave the token field blank and set the
  environment variable named by the config's `tokenEnv` (`GITHUB_TOKEN` by default) — the environment
  always takes precedence over a stored token.

The standalone CLI still works and reads the same files, so an existing deployment needs no change.

### Run history

Every run's record and its recorded output live under `KONTINUANCE_STORE`. Two backends serve them, and
`KONTINUANCE_STORE_BACKEND` picks:

| Backend | Layout under `KONTINUANCE_STORE` | Notes |
|---|---|---|
| `sqlite` (default) | one `kontinuance.db` | Back it up by copying that file; read it with the stock `sqlite3` CLI. Writes are atomic and listings are indexed. |
| `file` | `<id>.json` per run, `logs/<id>.log` per run | The original 006/018 layout. Every record is a text file you can `cat`. |

Starting on `sqlite` over a directory that already holds a file history **imports it once** — runs and
their output — and leaves every file where it was. So the switch needs no migration step, and setting the
backend back to `file` returns to exactly the previous state. The import only ever runs into an empty
database, so it cannot duplicate a run or overwrite one recorded since.

If you also run the standalone `kontinuance-ci` CLI, give it the **same** `KONTINUANCE_STORE` and
`KONTINUANCE_STORE_BACKEND` as the server. It resolves the history through the same code, so matching the
two variables is all it takes; mismatch them and the poller records runs the dashboard cannot see.

A misspelled backend name fails startup with a message naming it, rather than quietly serving an empty
history.

Neither backend changes what is durable across a restart — see
[Durability](#durability-only-paused-runs-survive-a-restart) below. Registered project descriptors,
event-source configuration, and the poll cursors stay as files beside the history either way; the
sign-in session registry is in memory and is cleared by a restart in both.

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

### `project:` (optional)

Names the project a pipeline belongs to, grouping several pipelines under one entry in the dashboard:

```yaml
pipeline:
  name: "relikquary-pr"
  project: "relikquary"
```

When absent, the project is inferred from the run's repository (the segment after the final `/`), so an
existing history needs no change.

A `project:` you declare must be letters, digits, and `. _ -`, 1–64 characters. Anything else — blank, a
stray space, a `/` — is rejected when the descriptor is parsed: the whole pipeline fails to load with a
`DescriptorException` naming the offending value. A malformed name is never rewritten or sanitized into
something that does match, because a name that silently became a *different* name would attribute runs to a
project you did not choose.

The inferred fallback is the one case that stays quiet. A repository name is not under the same constraint
(GitHub allows characters a project name does not), so a run whose repo short name fails the rule resolves
to **no project** rather than failing the run. Such a run stays visible in the unscoped list and simply
never groups under a project — declaring `project:` explicitly is the fix.

Projects with runs but no registered descriptor appear in the dashboard as **derived** — visible and
selectable, but not runnable until a descriptor is registered under the same name. Derived projects are
computed from the most recent `kontinuance.projects.derive-limit` runs (default 500); a project whose runs
have all aged past that window stops being listed.

**Scoping matches the run's *resolved* project, not the registered project's name.** If a project is
registered under a name that differs from its runs' repository short name, selecting it in the dashboard
shows zero runs — every run resolved to a different (derived) project. For example, a project registered
as `kontinuance-service` whose runs come from repo `khorum-oss/kontinuance` has those runs resolve to
`kontinuance` (the repo's short name), not `kontinuance-service`; selecting `kontinuance-service` in the
picker then shows no runs. The fix is to declare `project: kontinuance-service` in that pipeline's
descriptor, so future runs resolve to the registered name:

```yaml
pipeline:
  name: "kontinuance-ci"
  project: "kontinuance-service"   # matches the registered project name, not the repo's short name
```

If you select a project and its runs list is unexpectedly empty, check for exactly this mismatch before
assuming a bug.

**A second, distinct cause: the dashboard's run window is smaller than the derivation window.** The server
derives projects (and their run counts) from the most recent `kontinuance.projects.derive-limit` runs
(default 500), but the dashboard only loads the most recent 100 runs for the runs list. On a busy store, the
project picker can advertise a project with a healthy run count while its scoped runs list is empty —
not because of a name mismatch, but because that project's runs simply fall outside the browser's smaller
100-run window. Compare the project's `runCount` from `GET /api/projects` against how far back its runs sit
in `GET /api/runs`: if the mismatch above doesn't explain it, this is likely the culprit.

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
- **When both variables are unset the server runs open** (unauthenticated) and logs a warning at startup.
  The loopback bind (`127.0.0.1`) remains the safe default for open mode — the service is not
  network-reachable until you change `SERVER_ADDRESS` or front it with a proxy.
- **Setting only one of the two is a startup failure.** The server refuses to start rather than falling back
  to open mode, because a half-applied secret is indistinguishable from a deliberately open deployment: the
  API would serve every request unauthenticated while appearing configured. The failure names the missing
  property and never echoes a configured value.
- **`KONTINUANCE_AUTH_REQUIRED=true` makes credentials mandatory.** With it set, missing credentials are a
  startup failure instead of open mode. Set it on any deployment that must never run open — it is the guard
  for a secret that mounts empty or fails to populate *either* variable, which the half-set check alone
  cannot catch. Because the check runs during bean initialization, the process exits before the web server
  binds a port, so a misconfigured deployment can never serve an unauthenticated request. Under Kubernetes
  that surfaces as a crash-looping pod rather than a silently open API.

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
