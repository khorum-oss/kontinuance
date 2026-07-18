# Phase 1 Data Model: Containerized Deployment (P1)

No runtime data model. The "entities" are the deployment artifacts and their wiring.

## Artifact inventory

| Artifact | Path | Satisfies |
|---|---|---|
| Server image | `deploy/server.Dockerfile` | FR-001, FR-010, FR-012 |
| Web image | `deploy/web.Dockerfile` | FR-002 |
| nginx template | `deploy/web/default.conf.template` | FR-002, FR-003 |
| Compose (base) | `deploy/docker-compose.yml` | FR-004, FR-005, FR-006, FR-007 |
| Compose (dev override) | `deploy/docker-compose.dev.yml` | FR-009 |
| Env example | `deploy/.env.example` | FR-007, FR-008 |
| Deploy README | `deploy/README.md` | FR-011 |
| Build-context ignore | `.dockerignore` (repo root) | FR-012 (supports the builds) |

## Wiring (relationships)

- `web` (published) → proxies same-origin `/api`, `/api/runs/stream`, `/ws/runs` → `server` (internal).
- `server` → run store on named volume `kontinuance-runs`; descriptor mounted read-only; pipeline secrets
  from environment.
- base compose + dev override compose without editing the base.

## Deferred (named, not built here)

Kubernetes/Kustomize, ArgoCD/GitOps, stage→prod promotion scripts, a combined single image, secret-operator
integration, authentication, and a swappable run-store backend — later slices (P2+).
