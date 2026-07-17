# Phase 1 Data Model: Operator Configuration & Deployment Guide

This feature has no runtime data model. The "entities" are the documentation artifacts and the
configuration vocabulary they describe.

## Artifact inventory (what gets produced)

| Artifact | Path | Satisfies | Notes |
|---|---|---|---|
| Operator guide | `docs/running.md` | FR-001..003, FR-006..009 | Config surface, serving model, limitations, troubleshooting |
| Example descriptor | `docs/examples/kontinuance.yml` | FR-005, FR-008 | build → test → approval (own stage) → deploy; parser-valid |
| nginx proxy example | `docs/examples/nginx.conf` | FR-003, FR-004 | Static SPA + `/api` + `/ws/runs` upgrade, one origin |
| Caddy proxy example | `docs/examples/Caddyfile` | FR-003, FR-004 | Same, Caddy form |

## Configuration value (documentation entity)

A **configuration value** documented in the guide has:

- **name** — property and/or environment variable (e.g. `kontinuance.store` / `KONTINUANCE_STORE`).
- **default** — the value used when unset (from `contracts/config-surface.md`).
- **effect** — what changes at runtime when set.
- **scope** — server or web app.

The complete set is fixed by `contracts/config-surface.md`; the guide must cover all of it (SC-002).

## Documented behaviors (not data, but must be stated)

- **Authentication posture**: trigger/approve/reject are unauthenticated; loopback default; front with an
  authenticating proxy before exposure (FR-006).
- **Durability boundary**: gate-paused runs survive a restart; actively-running runs do not; durable gate
  assumes a single instance sharing one store (FR-007).
- **Fallback behavior**: a missing/invalid descriptor → trigger rejected, config screen shows fixtures;
  a missing coverage report → coverage falls back to fixtures (FR-009).
