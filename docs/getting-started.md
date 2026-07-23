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

1. **Sign in.** If the server is configured with `KONTINUANCE_AUTH_USERNAME` /
   `KONTINUANCE_AUTH_PASSWORD` (see [running.md](./running.md#authentication)), enter those credentials and
   click **SIGN IN** — a wrong pair is rejected with an error, a correct one establishes your session. If the
   server runs **open** (no credentials configured), the sign-in step is skipped and you land straight on the
   repo/project view.
2. **Select a repo setup** and click **ENTER MISSION CONTROL**. Your signed-in name shows in the sidebar;
   **EXIT** returns you here (the project view) without ending your session, and **SIGN OUT** (on this
   screen, when auth is enforced) ends the session and returns to sign-in.
3. **Runs.** The runs list live-updates over the stream. Click **RUN PIPELINE** to trigger the configured
   pipeline; the new run appears immediately.
4. **Open a run.** The run detail shows its **real step output** — the secret-masked, `[step] `-prefixed
   lines the pipeline produced — and refreshes while the run is still active. When a run reaches a
   manual-approval step it pauses (`WaitingOnApproval`, shown amber); click **APPROVE** to continue it or
   **REJECT** to end it. Approval is durable — it works from the persisted run, so it survives a restart.
5. **Explore the screens** from the sidebar: **Pipeline** (stage/task flow of a run), **Coverage** (the
   Kover report), **Config** (the resolved `kontinuance.yml` and its plan), and **Deploy** (a promotion
   view — currently a stub for an external ArgoCD/registry).

---

## Where the code comes from

Understand the workspace model before you write a real pipeline:

- **All steps of a run share one workspace directory.** It's created when the run starts and removed when
  it ends. A file a step writes at a relative path is visible to later steps — so you can **check out once
  and build across steps**.
- The workspace is isolated from the host (a step's `workingDir:` sub-path resolves *inside* it and can't
  escape), and secret masking + environment scoping stay per step.
- Use the **`git` checkout step** to fetch your source into the workspace:

  ```yaml
  - name: "checkout"
    git:
      url: "https://github.com/you/your-repo"
      ref: "main"              # optional branch/tag
      # dir: "."               # optional target sub-dir (default: the workspace root)
    secrets: ["GIT_TOKEN"]     # for a private repo, referenced from the environment
  ```

  Put the checkout **first** (the workspace starts empty, so cloning into `.` works), then later steps
  build the checked-out code.

The compose demo above uses `echo` steps so it runs with no external repo. For a **real** example — a
Kontinuance pipeline that checks out an actual Gradle app, builds it, and runs its tests, fresh, from one
local command — see [`sandbox/`](../sandbox/README.md):

```bash
sandbox/run.sh   # checkout → gradle assemble → tests, streaming the real logs; ends green, exit 0
```

It's a self-contained, offline, zero-dependency app that Kontinuance treats like any external repo. (Checkout
supports branch/tag refs today; arbitrary commit SHAs are a follow-up.)

## Authoring a pipeline

The server runs the pipeline from its configured descriptor (`kontinuance.config.descriptor`, default
`kontinuance.yml`). A gated pipeline that checks out its source, builds it, and gates before deploy:

```yaml
pipeline:
  name: "my-service"
  stages:
    - name: "checkout"
      steps:
        - name: "clone"
          git:
            url: "https://github.com/you/your-repo"
            ref: "main"
          secrets: ["GIT_TOKEN"]     # for a private repo
    - name: "build-and-test"
      steps:
        # Runs in the shared workspace where the checkout landed.
        - name: "build"
          gradle:
            tasks: ["build"]
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
one** of `run` / `gradle` / `docker` / `npm` / `approval` / `git`; the condition key is `when:`. Pipeline
secrets
are resolved from the server's environment variables and masked in logs. Full authoring rules and the
config surface are in [running.md](./running.md); runnable examples are in
[docs/examples/](./examples/).

> The typed `gradle:` / `npm:` / `docker:` steps assume the project is already present in the step's
> working directory — useful once a shared-workspace checkout exists, or within a single step that has
> already cloned into `workingDir`.

---

## Current limitations & planned work

Kontinuance is pre-1.0; some UI/UX pieces are still presentational. Known gaps, roughly by area:

**Source & workspace**
- Checkout supports **branch/tag refs** (shallow clone); an arbitrary commit **SHA** is a follow-up.
- The workspace lives for one run; a run **resumed after an approval gate** starts with a fresh (empty)
  workspace, so a checkout done *before* the gate isn't restored — keep post-gate steps self-sufficient.
  Persisting a workspace across a gate is a follow-up.
- Configuring a repo per project in the UI ("configure a repo for first setup") is still to come; today
  the checkout lives in the descriptor's `git:` step.

**Auth & session**
- **Authentication is real, opt-in, and wired end to end.** Set `KONTINUANCE_AUTH_USERNAME` /
  `KONTINUANCE_AUTH_PASSWORD` to enforce a login gate; the UI signs in against it (session cookie via
  `/api/auth/login`), shows the signed-in operator in the sidebar, and **EXIT** returns to the project view
  while **SIGN OUT** ends the session. With no credentials configured the server runs open (warns at
  startup) and the UI skips sign-in. See [running.md](./running.md#authentication).
- Still single-operator: multi-user accounts, roles, and external SSO/OAuth are future work; a session does
  not survive a server restart, and an expired session surfaces as failing calls rather than an automatic
  redirect back to sign-in.

**UI**
- **Light & dark themes with a brightness control** — toggle from the top bar; the choice follows your OS
  preference by default and is remembered per browser.
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
