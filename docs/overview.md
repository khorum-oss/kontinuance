# Overview

Kontinuance is a self-hosted CI/CD engine written in Kotlin. You describe a pipeline in a
`kontinuance.yml` descriptor (or the Kotlin DSL), and the engine runs it — with typed steps, secret
masking, manual approval gates, durable run history, and a live web dashboard over all of it.

It exists to be a CI/CD system you can run entirely on your own hardware, with no dependency on a
hosted runner fleet and no inbound network exposure.

## Design shape

Three concerns, separated from day one even while they share a process:

| Concern | What it does | Where it lives |
| --- | --- | --- |
| **Orchestrator** | Schedules pipelines, tracks state, serves the API | `:server` (Spring Boot, WebFlux + coroutines) |
| **Runner** | Executes steps — on the host or inside a container | `:engine` (`StepExecutor` / `StepSandbox` seams) |
| **Event source** | Turns GitHub activity into runs, reports status back | `:github` (outbound polling) |

Run history is persisted behind a `RunStore` seam (`:persistence`), file-backed by default so a
database can replace it without touching callers. The dashboard (`web/`, SvelteKit) reads the API and
subscribes to live updates over SSE and WebSocket.

## Decisions that shape everything else

**Pipelines are described in Kontinuance's own schema.** Descriptors are authored from scratch — never
copied or adapted from GitHub Actions workflows or any other CI system's YAML. A hybrid model: YAML for
the common case, with a Kotlin DSL escape hatch for pipelines that need real logic.

**External CI is poll-first.** Kontinuance polls the GitHub API (outbound only, so a private network
needs no inbound exposure), runs the matching pipeline, and posts a commit status on the head SHA under
a stable `kontinuance/ci` context. A required status check then gates the merge. A signature-verified
webhook is an optional lower-latency mode later, never a prerequisite.

**Commit Status API, not Checks.** The Commit Status API works with a personal access token; the Checks
API needs a GitHub App, which is deferred.

**Status is an explicit FSM.** `PipelineStatus` is a sealed hierarchy including the `WaitingOnApproval`
state that makes manual promotion gates real, with transitions published as a `Flow` — which is what
lets the UI show live status without polling.

**Secrets are injected, never stored.** A `SecretSource` seam resolves secrets by name (environment
variables today), and every log line passes through a masking sink on the way out.

**Isolation is a seam, not an assumption.** Steps run on the host by default; a step that declares an
`image:` runs inside that container via `StepSandbox`, with the workspace bind-mounted and secrets
forwarded by name. A Kubernetes backend fits the same seam.

## Phasing

- **v0** — in-process executor, `ProcessBuilder` steps, logs to stdout ✅
- **v1** — persistent state, container isolation, GitHub trigger, web UI ✅
- **v2** — multi-agent execution, richer secret management, DSL maturity
- **v3** — plugin system, artifact storage, build caching

See [roadmap.md](roadmap.md) for where each numbered feature landed and what remains, and
[getting-started.md](getting-started.md) to run it locally.
