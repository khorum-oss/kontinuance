# Kontinuance

A self-hosted CI/CD engine in Kotlin. Start with [`docs/overview.md`](docs/overview.md) for the design
and [`docs/roadmap.md`](docs/roadmap.md) for what is built and what remains — the roadmap's "Current
state" table is the authoritative record of each numbered feature.

## Layout

| Module | What it is |
| --- | --- |
| `engine` | Pipeline model, strict YAML descriptor parser, Kotlin DSL, execution, secret masking |
| `persistence` | `RunStore` / `RunLogStore` — durable run history behind a swappable seam |
| `github` | GitHub event source: polling, trigger resolution, commit-status reporting |
| `server` | Spring Boot 4.1 (WebFlux + coroutines) API over the stores, SSE + WebSocket streams |
| `web` | SvelteKit 5 (runes) dashboard, Vitest + Playwright |
| `dsl`, `core-test`, `integration-tests` | Shared DSL scaffolding, test helpers, cross-module ITs |

## Commands

```bash
./gradlew check                  # JVM: tests + detekt across modules
./gradlew :server:test           # one module
pnpm -C web test                 # web unit tests (Vitest)
pnpm -C web exec svelte-check    # web typecheck
pnpm -C web exec playwright test # web e2e (set PW_CHROMIUM_PATH in a sandbox)
```

Run the whole app locally with `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up`,
or from source per [`docs/getting-started.md`](docs/getting-started.md).

## Conventions

- **Dependency verification stays enabled** (`gradle/verification-metadata.xml`). A change that needs a
  new artifact adds a trust entry; it never disables verification.
- **Descriptors are Kontinuance's own schema** — never copied or adapted from GitHub Actions or another
  CI system's YAML.
- **Secrets are resolved by name and masked in logs.** Never inline a secret value, and never return one
  from an API read.
- Each feature has a `specs/NNN-name/spec.md`. Keep them short; the roadmap carries the running summary.
