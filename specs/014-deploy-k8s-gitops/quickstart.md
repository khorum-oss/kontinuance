# Quickstart / Verification: Kubernetes + GitOps Deployment (P2)

## In this repo (authoring checks — always available)

- **Every manifest is valid YAML**: parse all `deploy/k8s/**`, `deploy/argocd/**`, and the CI workflow
  with a YAML loader (multi-doc). Expected: no parse errors.
- **Scripts are valid shell**: `bash -n deploy/pipeline/release.sh deploy/pipeline/promote.sh`. Expected:
  no syntax errors; each has a usage/help path and `set -euo pipefail`.
- **Sync policy**: `application-stage.yaml` contains `syncPolicy.automated`; `application-prod.yaml` does
  **not** (manual sync). Overlays set distinct namespaces and image tags; prod keeps `replicas: 1`.

## On a machine with kubectl/kustomize (authoritative)

```bash
kubectl kustomize deploy/k8s/overlays/stage    # -> valid namespaced, tagged manifests
kubectl kustomize deploy/k8s/overlays/prod     # -> single replica, prod host/tag
```

Expected: both build without error; stage is namespace `kontinuance-stage` tag `stage`, prod is
`kontinuance-prod` tag `stable`, each with one server replica, the run-store PVC, ConfigMap/Secret, the
descriptor ConfigMap, and an Ingress to the web.

## On a cluster (operator)

Apply an overlay (`kubectl apply -k deploy/k8s/overlays/stage`) or register the ArgoCD Applications
(`kubectl apply -f deploy/argocd/`). Stage auto-syncs; prod waits for `argocd app sync kontinuance-prod`.
The UI is reachable at the Ingress host; run history survives a pod restart (PVC); liveness/readiness
probes report healthy.

## CI (authoritative for image builds)

`.github/workflows/deploy-images.yml` builds both images from source on `deploy/**` changes — the image
build coverage the P1 sandbox could not provide.

## Constraints (SC-005)

`git status` shows no `engine/`/`server/`/`web/` application code changed; no secret values (placeholders
only); no external design links.
