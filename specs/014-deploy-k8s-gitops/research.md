# Phase 0 Research: Kubernetes + GitOps Deployment (P2)

## Decision: one origin via Ingress → web, web fronts the server (same model as compose)

- **Decision**: Ingress routes the environment host to the **web** Service; the web container proxies
  `/api`, `/api/runs/stream`, and `/ws/runs` to the **server** Service (`http://kontinuance-server:8077`).
- **Rationale**: identical single-origin model to the P1 compose stack and to relikquary's nginx frontend;
  the browser needs no CORS. The server Service stays ClusterIP (internal).
- **Alternatives**: Ingress splitting `/api` to server and `/` to web — more Ingress rules, and the web
  image already proxies; rejected.

## Decision: single-writer run store → single replica, RWO PVC

- **Decision**: a `ReadWriteOnce` PVC mounted at `/data/runs`; server `replicas: 1` in every overlay
  (including prod).
- **Rationale**: the run store is file-backed and single-writer (matches the documented durability model);
  multiple replicas would corrupt/split history. A shared backend for HA is a named later slice.

## Decision: enable probes via ConfigMap env, not a source change (FR-009)

- **Decision**: set `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true` (and keep health exposed) in the
  ConfigMap; wire liveness → `/actuator/health/liveness`, readiness → `/actuator/health/readiness`.
- **Rationale**: Spring's relaxed binding maps the env var to the property, lighting up the probe
  endpoints without editing `application.yml` — so no application code/config file changes. Matches
  relikquary's probe paths.
- **Alternatives**: probe `/api/health` for both (works, but no liveness/readiness distinction — weaker);
  editing `application.yml` (a source change — rejected by FR-009).

## Decision: descriptor as a generated ConfigMap reusing deploy/kontinuance.yml (DRY)

- **Decision**: `configMapGenerator` builds a `descriptor` ConfigMap from the existing
  `deploy/kontinuance.yml`; mounted read-only at `/etc/kontinuance/kontinuance.yml`.
- **Rationale**: reuses the P1 demo descriptor; one source of truth for the sample pipeline.

## Decision: images via kustomize `images:`; overlays set registry/tag; promote edits the tag

- **Decision**: base Deployments reference `kontinuance-server` / `kontinuance-web`; base `images:` maps
  them to a placeholder registry; each overlay sets `newTag` (`stage` / `stable`). `release.sh` pins the
  stage tag; `promote.sh` copies stage's tag to prod.
- **Rationale**: standard Kustomize image transformation; the tag is the single field GitOps promotion
  changes (a git commit ArgoCD then syncs). Matches relikquary's release/promote scripts.

## Decision: ArgoCD stage auto-sync, prod manual (the gate)

- **Decision**: `application-stage.yaml` sets `syncPolicy.automated` (prune + selfHeal);
  `application-prod.yaml` omits `automated` (manual sync only). An `AppProject` scopes both sources/dests.
- **Rationale**: the production gate is a human `argocd app sync` — mirrors relikquary and complements the
  in-app approval gate (the two are independent gates at different layers).

## Decision: CI builds images on deploy changes (closes the P1 gap)

- **Decision**: `.github/workflows/deploy-images.yml` builds the server + web images from source on
  `deploy/**` changes and manual dispatch; no push. Verification stays enabled in the server build.
- **Rationale**: GitHub-hosted runners have Docker + unrestricted network (no intercepting proxy), so the
  image builds that could not complete in the P1 sandbox get real coverage here.
