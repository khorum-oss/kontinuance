# Kontinuance

A self-hosted CI/CD engine you run on your own stack. Describe a pipeline in a `kontinuance.yml` (or the
Kotlin DSL); the engine runs it with typed steps, secret masking, and manual-approval gates, and a live
web UI shows run history, coverage, and the pipeline flow.

## Highlights

- **Pipelines as data or code** — the same model from a strict `kontinuance.yml` descriptor or a typed
  Kotlin DSL. Step types: `run` (shell), `gradle`, `docker`, `npm`, and `approval` (a manual gate).
- **Structured-concurrency engine** — ordered stages, bounded parallelism, per-step isolation, timeouts,
  and secret masking in logs.
- **Durable manual-approval gates** — a run pauses at a gate and resumes on approval, surviving a restart.
- **Live web UI** — runs list, run detail with approve/reject, pipeline flow, coverage (Kover), and the
  resolved config, updating over SSE/WebSocket.
- **Runs on your stack** — a Spring Boot server + a static SPA, containerized, with Kubernetes + ArgoCD
  deployment manifests.

## Quick start

```bash
cp deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
# open http://localhost:8080  →  sign in, RUN PIPELINE, approve the gated run
```

New here? Start with **[Getting Started](docs/getting-started.md)** — it covers running locally (Compose
or from source), a UI walkthrough, authoring a pipeline, and current limitations.

> Pre-1.0: opt-in authentication is wired end to end (`KONTINUANCE_AUTH_USERNAME` /
> `KONTINUANCE_AUTH_PASSWORD`; open on loopback when unset — see
> [running.md](docs/running.md#authentication)), and some UI pieces are still presentational. See
> [Getting Started → limitations](docs/getting-started.md#current-limitations--planned-work).

## Documentation

- **[Getting Started](docs/getting-started.md)** — run & test locally, UI walkthrough, authoring
- [Overview](docs/overview.md) — what Kontinuance is and where it's headed
- [Running & configuring](docs/running.md) — the full configuration surface, serving model, limitations
  - Examples: [`kontinuance.yml`](docs/examples/kontinuance.yml) ·
    [`nginx.conf`](docs/examples/nginx.conf) · [`Caddyfile`](docs/examples/Caddyfile)
- [Deploying](deploy/README.md) — containers & Compose · [Kubernetes](deploy/k8s/README.md) ·
  [ArgoCD/GitOps](deploy/argocd/README.md)
- [CLI](docs/cli.md) · [Roadmap](docs/roadmap.md)

## Repository layout

| Module | What it is |
|---|---|
| `engine` | The pipeline model, descriptor/DSL front-ends, and the in-process execution engine |
| `persistence` | Run history storage (file-backed run store) |
| `server` | The Spring Boot API + live stream over the engine |
| `web` | The SvelteKit single-page UI |
| `github` | GitHub event source (in progress) |
| `deploy` | Dockerfiles, Compose, Kubernetes/Kustomize, ArgoCD, promotion scripts |
