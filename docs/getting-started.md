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

## Where the code comes from (important)

**Kontinuance does not check out your source for you (yet).** Understand the workspace model before you
write a real pipeline:

- Each step runs in **its own fresh temporary directory, which is deleted when the step finishes**. Steps
  are fully isolated: there is **no shared workspace** between them, and a step **cannot** reach files on
  the host (the `workingDir:` hint is resolved *inside* that temp directory).
- There is **no built-in `git checkout`** and **no repo/SHA driving a checkout**. The `repo`/`sha` you may
  see on a run are metadata only.

So a bare `gradle: { tasks: ["assemble"] }` step would start in an **empty** directory and fail — there's
no project there. Two consequences:

1. **To build real code today, a single step must fetch *and* build it**, because the checkout can't be
   shared with later steps:

   ```yaml
   - name: "build"
     run: "git clone --depth 1 https://github.com/you/your-repo src && cd src && ./gradlew assemble"
     secrets: ["GIT_TOKEN"]   # for a private repo, referenced from the environment
   ```

2. **You cannot clone in one step and build in the next** — the second step gets a new empty directory.

This is why the shipped demo uses `echo` steps: it exercises the engine, the approval gate, and the UI
end to end **without** needing external code. A first-class **source checkout + a shared per-run
workspace** is the key missing piece (it's what "configure a repo for first setup" will drive) — see
[limitations](#current-limitations--planned-work).

## Authoring a pipeline

The server runs the pipeline from its configured descriptor (`kontinuance.config.descriptor`, default
`kontinuance.yml`). A gated pipeline that clones its own source (per the note above) and gates before
deploy:

```yaml
pipeline:
  name: "my-service"
  stages:
    - name: "build-and-test"
      steps:
        # One self-contained step: fetch the source, then build + test it in the same working directory.
        - name: "clone-build-test"
          run: "git clone --depth 1 https://github.com/you/your-repo src && cd src && ./gradlew build"
          secrets: ["GIT_TOKEN"]
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

> The typed `gradle:` / `npm:` / `docker:` steps assume the project is already present in the step's
> working directory — useful once a shared-workspace checkout exists, or within a single step that has
> already cloned into `workingDir`.

---

## Current limitations & planned work

Kontinuance is pre-1.0; some UI/UX pieces are still presentational. Known gaps, roughly by area:

**Source & workspace** (the most foundational gap)
- **No built-in source checkout, and no shared workspace across steps.** Every step runs in its own
  fresh, ephemeral, isolated directory (see [Where the code comes from](#where-the-code-comes-from-important)).
  Today, building real code means a single step clones and builds it; multi-step pipelines can't share a
  checkout. *Planned: a first-class checkout that fetches a configured repo/ref into a per-run workspace
  the steps share — tied to "configure a repo for first setup".*

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
