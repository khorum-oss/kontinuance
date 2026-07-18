# Phase 1 Data Model: Kubernetes + GitOps Deployment (P2)

No runtime data model. Entities = the deployment artifacts.

## Artifact inventory

| Artifact | Path | Satisfies |
|---|---|---|
| Kustomize base | `deploy/k8s/base/*` | FR-001, FR-002, FR-003 |
| Stage overlay | `deploy/k8s/overlays/stage/kustomization.yaml` | FR-004 |
| Prod overlay | `deploy/k8s/overlays/prod/kustomization.yaml` | FR-004 |
| AppProject | `deploy/argocd/project.yaml` | FR-005 |
| Stage Application | `deploy/argocd/application-stage.yaml` | FR-005 |
| Prod Application | `deploy/argocd/application-prod.yaml` | FR-005 |
| Release script | `deploy/pipeline/release.sh` | FR-006 |
| Promote script | `deploy/pipeline/promote.sh` | FR-006 |
| CI image build | `.github/workflows/deploy-images.yml` | FR-007 |
| K8s README | `deploy/k8s/README.md` | FR-010 |
| ArgoCD README | `deploy/argocd/README.md` | FR-010 |

## Relationships

- Ingress → web Service → (nginx proxy) → server Service → PVC (run store) + descriptor ConfigMap + Secret.
- Overlays patch the base (namespace/tag/host/replicas). ArgoCD Applications point at overlay paths.
- release/promote scripts edit the overlays' image tag; ArgoCD syncs the resulting git state.
- CI builds the feature-013 images.

## Deferred (named, not built here)

Secret-operator integration (external-secrets/1Password), multi-replica + shared run-store backend,
authentication, Helm packaging, and a combined single image.
