# Kontinuance on ArgoCD

GitOps delivery for Kontinuance: **stage auto-syncs**, **prod is a manual gate**.

## Register

Requires ArgoCD installed in the `argocd` namespace. Point the manifests' `repoURL` at your fork if
needed, then:

```bash
kubectl apply -f deploy/argocd/project.yaml
kubectl apply -f deploy/argocd/application-stage.yaml
kubectl apply -f deploy/argocd/application-prod.yaml
```

- **`project.yaml`** — an AppProject scoping the source repo and the two destination namespaces.
- **`application-stage.yaml`** — tracks `deploy/k8s/overlays/stage` with an **automated** sync policy
  (`prune` + `selfHeal`): committed changes roll out to `kontinuance-stage` immediately.
- **`application-prod.yaml`** — tracks `deploy/k8s/overlays/prod` with **no** automated policy: prod only
  changes on an explicit `argocd app sync kontinuance-prod`.

## Promotion flow

```bash
# 1. Build, push, and pin the stage tag (stage auto-syncs on commit).
REGISTRY=ghcr.io/khorum-oss deploy/pipeline/release.sh
git commit -am "release: stage" && git push

# 2. Once stage is verified, promote the tested tag to prod and sync deliberately.
deploy/pipeline/promote.sh
git commit -am "promote: prod" && git push
argocd app sync kontinuance-prod
```

The manual `argocd app sync` is the production gate at the delivery layer — complementary to Kontinuance's
in-pipeline **approval gate** (they gate different things: image promotion vs. a step within a run).

## Not included (later slices)

A secret-operator integration for the cluster Secret, and multi-cluster / multi-replica topologies.
