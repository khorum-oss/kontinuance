# Implementation Plan: Operator Configuration & Deployment Guide

**Branch**: `claude/operator-configuration` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-operator-configuration/spec.md`

## Summary

Add an operator-facing configuration & deployment guide plus copyable example artifacts so a team can
run Kontinuance (server + web UI) in their own environment. No engine/server/web code changes: the work
is `docs/running.md`, an example gated descriptor (`docs/examples/kontinuance.yml`), and reverse-proxy
examples (`docs/examples/nginx.conf`, `docs/examples/Caddyfile`). Every configuration value and behavior
is sourced from the existing implementation (features 007–011); nothing new is invented, and the example
descriptor is validated against the real strict parser.

## Technical Context

**Language/Version**: Markdown docs + YAML/conf examples (no compiled code). Reference targets: Kotlin
2.3.21 / JDK 21 server, SvelteKit/Svelte 5 static web app.

**Primary Dependencies**: None added. Documents existing runtime config (Spring Boot server, `adapter-static`
web app) and existing tooling; introduces no library or plugin.

**Storage**: N/A (documents the existing `FileRunStore` run directory as a config value).

**Testing**: Docs/config review against the source-of-truth contracts (see `contracts/`), plus a
lightweight parser check that loads `docs/examples/kontinuance.yml` through the real
`PipelineDescriptor.parse` to prove validity. No runtime code is added to exercise.

**Target Platform**: Linux server (loopback by default); any reverse proxy (nginx/Caddy) fronting it.

**Project Type**: Documentation & example configuration (web-service + static SPA it describes).

**Performance Goals**: N/A (documentation).

**Constraints**: No external design link (Principle-adjacent, per spec FR-010); no secrets committed
(Principle V) — placeholder env var names only; no new dependencies, so `verification-metadata.xml` is
untouched (Principle V).

**Scale/Scope**: One guide + one example descriptor + one/two proxy examples; ~3–5 new files under `docs/`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Platform-First & Stable Public Contract**: PASS — no public contract changes; the guide *documents*
  the existing `/api` + `/ws` surface and config, and must match it (that is the point of the `contracts/`
  source-of-truth check).
- **II. Test-First & Integration-Verified**: PASS (adapted) — no behavior change, so no new integration
  test is required. The feature's verification is a docs review plus parsing the example descriptor with
  the real parser (`quickstart.md`).
- **III. Quality Gates Are Non-Negotiable**: PASS — no Kotlin/TS code changes, so detekt/Kover/Sonar are
  unaffected; the existing gates stay green. Markdown/YAML/conf are not gated but are review-checked.
- **IV. Correct, Covered Code Generation**: N/A — no code generation touched.
- **V. Supply-Chain Integrity & Reproducible Publishing**: PASS — no new dependency (verification metadata
  untouched); no secrets committed (placeholder names only); nothing published.

No violations → Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/012-operator-configuration/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (configuration-value inventory)
├── quickstart.md        # Phase 1 output (how to verify the guide + example)
├── contracts/           # Phase 1 output (source-of-truth the docs are checked against)
│   ├── config-surface.md
│   └── descriptor-validity.md
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
docs/
├── running.md                     # NEW — operator configuration & deployment guide
└── examples/
    ├── kontinuance.yml            # NEW — strict-parser-valid gated descriptor
    ├── nginx.conf                 # NEW — reverse proxy: static SPA + /api + /ws upgrade
    └── Caddyfile                  # NEW — same, Caddy form
```

No changes under `engine/`, `server/`, `web/`, or `gradle/`.

**Structure Decision**: Documentation-and-examples feature. All new files live under `docs/` at the
repository root; the existing `docs/` directory (overview.md, roadmap.md, cli.md) is the home for
`running.md`, and a new `docs/examples/` holds the copyable artifacts. No source module is modified, so
no `settings.gradle.kts` or build change is needed.

## Complexity Tracking

> No Constitution Check violations — no entries.
