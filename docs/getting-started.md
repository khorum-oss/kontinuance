# Getting Started with Kontinuance

Kontinuance is a self-hosted CI/CD engine: you describe a pipeline in a `kontinuance.yml` (or the Kotlin
DSL), and the engine runs it — with typed steps, secret masking, manual-approval gates, and a live web UI
showing run history, coverage, and the pipeline flow.

This guide gets you running and testing **locally**, two ways:

- **[A. Docker Compose](#a-run-with-docker-compose-easiest)** — one command, the whole app.
- **[B. From source](#b-run-from-source-for-development)** — server + UI with hot reload.

Then it walks the [UI](#using-the-ui), [how to author a pipeline](#authoring-a-pipeline), and the
[current limitations](#current-limitations--planned-work).

---

## A. Run with Docker Compose (easiest)

Requires Docker + `docker compose`.

```bash
cp deploy/.env.example deploy/.env          # placeholders are fine to start
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
```

Open **http://localhost:8080**. The stack ships with a demo pipeline (`deploy/kontinuance.yml`:
`build → test → approve → deploy`) whose steps are simple shell commands, so a triggered run actually
reaches the approval gate and you can approve it from the UI.

- Run history persists in a named volume (survives restarts; `docker compose down -v` clears it).
- Point it at your own pipeline by editing the `deploy/kontinuance.yml` mount, and set pipeline secrets in
  `deploy/.env`.

See [deploy/README.md](../deploy/README.md) for the container details and
[running.md](./running.md) for the full configuration surface.

---

## B. Run from source (for development)

Requires JDK 21 and [pnpm](https://pnpm.io) (via `corepack enable`).

**Terminal 1 — the server** (the `application` plugin's `run` task). Point it at a gated descriptor so you
can exercise the approval flow:

```bash
KONTINUANCE_CONFIG_DESCRIPTOR="$(pwd)/deploy/kontinuance.yml" \
KONTINUANCE_STORE="$(pwd)/.local/runs" \
  ./gradlew :server:run
```

The server listens on `127.0.0.1:8077` (loopback). Health check: `curl http://localhost:8077/api/health`.

**Terminal 2 — the web UI** (Vite dev server, hot reload; it proxies `/api` and `/ws` to the server):

```bash
pnpm --dir web install
pnpm --dir web dev
```

Open the URL Vite prints (usually **http://localhost:5173**). To point the dev proxy at a server on a
different address, set `KONTINUANCE_API` before `pnpm --dir web dev`.

> The pipeline's `run:` steps execute via `/bin/sh`, so from-source runs need a POSIX shell (macOS/Linux,
> or WSL on Windows).

---

## Using the UI

1. **Sign in.** Enter any username and password and click **SIGN IN** — authentication is not yet enforced
   (see [limitations](#current-limitations--planned-work)).
2. **Select a repo setup** and click **ENTER MISSION CONTROL**.
3. **Runs.** The runs list live-updates over the stream. Click **RUN PIPELINE** to trigger the configured
   pipeline; the new run appears immediately.
4. **Approve a gated run.** When a run reaches a manual-approval step it pauses (`WaitingOnApproval`,
   shown amber). Open the run and click **APPROVE** to continue it, or **REJECT** to end it. Approval is
   durable — it works from the persisted run, so it survives a server restart.
5. **Explore the screens** from the sidebar: **Pipeline** (stage/task flow of a run), **Coverage** (the
   Kover report), **Config** (the resolved `kontinuance.yml` and its plan), and **Deploy** (a promotion
   view — currently a stub for an external ArgoCD/registry).

---

## Authoring a pipeline

The server runs the pipeline from its configured descriptor (`kontinuance.config.descriptor`, default
`kontinuance.yml`). A minimal gated pipeline:

```yaml
pipeline:
  name: "my-service"
  stages:
    - name: "build"
      steps:
        - name: "assemble"
          gradle:
            tasks: ["assemble"]
    - name: "test"
      steps:
        - name: "unit"
          gradle:
            tasks: ["test"]
    # Put the approval gate in its OWN stage — a resumed run then repeats no prior work.
    - name: "approve"
      steps:
        - name: "promote-to-prod"
          approval: "Promote this build to production?"
    - name: "deploy"
      steps:
        - name: "rollout"
          run: "./deploy.sh"
          secrets: ["DEPLOY_TOKEN"]
```

Parser rules (strict — unknown keys are rejected): top-level `pipeline:`; each step declares **exactly
one** of `run` / `gradle` / `docker` / `npm` / `approval`; the condition key is `when:`. Pipeline secrets
are resolved from the server's environment variables and masked in logs. Full authoring rules and the
config surface are in [running.md](./running.md); runnable examples are in
[docs/examples/](./examples/).

---

## Current limitations & planned work

Kontinuance is pre-1.0; some UI/UX pieces are still presentational. Known gaps, roughly by area:

**Auth & session**
- **Authentication is not enforced.** The sign-in screen is a presentational gate and the API endpoints
  (including trigger/approve/reject) are unauthenticated — run it on loopback or behind an authenticating
  proxy (see [running.md](./running.md#security-no-authentication-yet)). *Planned: real authentication.*
- **The operator name in the sidebar is a placeholder**, not the signed-in user. *Planned: reflect the
  logged-in person.*
- **EXIT returns to the sign-in screen.** *Planned: return to the project/repo view instead.*

**UI**
- **Dark theme only** — no light mode or brightness control yet. *Planned.*
- **The repo-setup screen is presentational** (demo repos). *Planned: redesign, plus first-run repo
  configuration.*

**Pipeline configuration**
- **No in-app editing of `kontinuance.yml`.** Today you edit the file the server points at. *Planned: edit
  it in the UI, with the option for a pipeline to be project-provided or locked.*

**Engine/ops**
- Only runs **paused at an approval gate** survive a restart; an actively-executing run does not (in-process
  engine). The durable approval gate assumes a **single instance** sharing one run store.
- The **Deploy** screen is a stub; ArgoCD/registry integration is external.

See [roadmap.md](./roadmap.md) for the broader direction.
