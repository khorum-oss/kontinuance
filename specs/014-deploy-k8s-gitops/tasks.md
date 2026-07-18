# Tasks: Kubernetes + GitOps Deployment (P2)

**Feature**: 014-deploy-k8s-gitops | **Branch**: `claude/deploy-k8s`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Deployment/GitOps + CI — **no engine/server/web application code changes**. Traces to
[contracts/deploy-topology.md](./contracts/deploy-topology.md). Builds on feature 013's images (on `main`).

**MVP** = User Story 1 (deploy to a cluster with Kustomize).

---

## Phase 1: Setup

- [x] T001 [P] Create `deploy/k8s/base/`, `deploy/k8s/overlays/{stage,prod}/`, `deploy/argocd/`, `deploy/pipeline/`

## Phase 2: Foundational

- [x] T002 Re-verify [contracts/deploy-topology.md](./contracts/deploy-topology.md) against `deploy/kontinuance.yml`, the server config surface, and feature 013's image names — blocks the manifests

---

## Phase 3: User Story 1 — Deploy to a cluster with Kustomize (Priority: P1) 🎯 MVP

- [x] T003 [P] [US1] `deploy/k8s/base/configmap.yaml` — non-secret settings incl. `SERVER_ADDRESS=0.0.0.0`, `KONTINUANCE_STORE=/data/runs`, `KONTINUANCE_CONFIG_DESCRIPTOR=/etc/kontinuance/kontinuance.yml`, stream tuning, `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true` (FR-002, FR-009)
- [x] T004 [P] [US1] `deploy/k8s/base/secret.yaml` — placeholder pipeline secrets (`DEPLOY_TOKEN`) via stringData, no real values (FR-001, FR-008)
- [x] T005 [P] [US1] `deploy/k8s/base/pvc.yaml` — `ReadWriteOnce` run-store PVC (FR-001)
- [x] T006 [P] [US1] `deploy/k8s/base/server-deployment.yaml` — image `kontinuance-server`, `replicas: 1`, `envFrom` config+secret, PVC mount at `/data/runs`, descriptor ConfigMap mount at `/etc/kontinuance`, non-root securityContext, liveness `/actuator/health/liveness` + readiness `/actuator/health/readiness` on 8077 (FR-002)
- [x] T007 [P] [US1] `deploy/k8s/base/server-service.yaml` — ClusterIP :8077
- [x] T008 [P] [US1] `deploy/k8s/base/web-deployment.yaml` — image `kontinuance-web`, `KONTINUANCE_BACKEND=http://kontinuance-server:8077`, readiness `GET /` on 80 (FR-003)
- [x] T009 [P] [US1] `deploy/k8s/base/web-service.yaml` — ClusterIP :80
- [x] T010 [P] [US1] `deploy/k8s/base/ingress.yaml` — one host → `kontinuance-web:80` (FR-001, FR-003)
- [x] T011 [US1] `deploy/k8s/base/kustomization.yaml` — resources (all base manifests), `namePrefix: kontinuance-`, common labels, `configMapGenerator` `descriptor` from `../../kontinuance.yml`, `images` mapping `kontinuance-server`/`kontinuance-web` to a placeholder registry
- [x] T012 [P] [US1] `deploy/k8s/overlays/stage/kustomization.yaml` — `namespace: kontinuance-stage`, image `newTag: stage`, Ingress host patch, `replicas: 1` (FR-004)
- [x] T013 [P] [US1] `deploy/k8s/overlays/prod/kustomization.yaml` — `namespace: kontinuance-prod`, image `newTag: stable`, prod host, `replicas: 1` (single-writer) (FR-004)

**Checkpoint**: US1 — both overlays build into valid, namespaced, tagged manifests.

---

## Phase 4: User Story 2 — GitOps: stage auto, prod gated (Priority: P2)

- [x] T014 [P] [US2] `deploy/argocd/project.yaml` — AppProject `kontinuance` scoping the repo source + stage/prod destination namespaces (FR-005)
- [x] T015 [P] [US2] `deploy/argocd/application-stage.yaml` — path `deploy/k8s/overlays/stage`, dest namespace `kontinuance-stage`, `syncPolicy.automated` (prune + selfHeal) (FR-005)
- [x] T016 [P] [US2] `deploy/argocd/application-prod.yaml` — path `deploy/k8s/overlays/prod`, dest namespace `kontinuance-prod`, **no** automated policy (manual sync) (FR-005)
- [x] T017 [P] [US2] `deploy/argocd/README.md` — install/register + the stage-auto/prod-gated flow (FR-010)

**Checkpoint**: US2 — stage auto-syncs, prod is a manual gate.

---

## Phase 5: User Story 3 — Promotion scripts (Priority: P3)

- [x] T018 [P] [US3] `deploy/pipeline/release.sh` — `set -euo pipefail`, usage/help; build + push `kontinuance-server`/`-web` to `$REGISTRY:$TAG`; set the stage overlay image `newTag` (FR-006)
- [x] T019 [P] [US3] `deploy/pipeline/promote.sh` — `set -euo pipefail`, usage/help; set the prod overlay image `newTag` to the stage overlay's current tag (FR-006)

**Checkpoint**: US3 — one-command release + promote.

---

## Phase 6: User Story 4 — CI image build (Priority: P3)

- [x] T020 [P] [US4] `.github/workflows/deploy-images.yml` — on `deploy/**` push/PR + `workflow_dispatch`: build the server and web images from source (no push), verification enabled (FR-007)

---

## Phase 7: Polish & Cross-Cutting

- [x] T021 `deploy/k8s/README.md` — apply overlays, register ArgoCD, the release/promote flow; state that a secret-operator integration and multi-replica scaling are later slices (FR-010)
- [x] T022 Verify: YAML-parse every `deploy/k8s/**`, `deploy/argocd/**`, and the CI workflow (multi-doc); `bash -n` the two scripts; confirm stage has `automated` sync and prod does not, and prod keeps `replicas: 1` (SC-001, SC-002, SC-003)
- [x] T023 [P] Confirm no secret values and no external design links anywhere in `deploy/**` / the workflow, and that `git status` shows no `engine/`/`server/`/`web/` application code changed (FR-008, FR-009, SC-005)

---

## Dependencies

- Setup (T001) → all. Foundational (T002) → manifests.
- US1 base manifests (T003–T010) are `[P]`; the base `kustomization.yaml` (T011) references them all; the
  overlays (T012, T013) reference the base.
- US2 (T014–T017), US3 (T018–T019), US4 (T020) are independent of each other (all `[P]`), after US1.
- Polish (T021–T023) → last.

## Parallel Opportunities

- Each base manifest (T003–T010), both overlays (T012/T013), all ArgoCD files (T014–T017), both scripts
  (T018/T019), the CI workflow (T020), and the review (T023) are `[P]`. The base `kustomization.yaml`
  (T011) is the join point after the base manifests exist.

## Implementation Strategy

- **MVP**: Phase 1–3 (US1) → deployable overlays. **Increment**: US2 (GitOps), US3 (promotion), US4 (CI),
  Polish. **Verification**: YAML parse + `bash -n` here; `kustomize build` + the CI image build authoritative.
