# Kontinuance on Kubernetes

Kustomize manifests for deploying Kontinuance to a cluster. Builds on the container images from
[`../server.Dockerfile`](../server.Dockerfile) / [`../web.Dockerfile`](../web.Dockerfile). For the full
runtime configuration surface, see [`../../docs/running.md`](../../docs/running.md).

## Layout

```
k8s/
├── base/                 # server + web Deployments/Services, Ingress (one origin), run-store PVC,
│                         # ConfigMap, Secret, and the descriptor ConfigMap (from base/kontinuance.yml)
└── overlays/
    ├── stage/            # namespace kontinuance-stage, image tag `stage`, stage host
    └── prod/             # namespace kontinuance-prod, image tag `stable`, prod host — single replica
```

## Topology

- The **Ingress** routes one host to the **web** Service; the web container proxies `/api`, the SSE
  stream, and `/ws/runs` to the **server** Service (`http://kontinuance-server:8077`) — one origin.
- The server reads run history from a **ReadWriteOnce PVC** and its pipeline descriptor from a mounted
  **ConfigMap**; config comes from the ConfigMap, pipeline secrets from the Secret.
- **Single server replica** everywhere: the run store is single-writer. Multi-replica needs a shared
  run-store backend (a later slice).
- Liveness/readiness probes hit `/actuator/health/{liveness,readiness}`, enabled via the ConfigMap's
  `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED` (no application code change).

## Apply directly

```bash
# Set your registry/tag in the overlay (or run deploy/pipeline/release.sh), then:
kubectl apply -k deploy/k8s/overlays/stage
kubectl apply -k deploy/k8s/overlays/prod
```

Preview what an overlay renders:

```bash
kubectl kustomize deploy/k8s/overlays/stage
```

## Configure

- **Images**: each overlay's `images:` sets the registry (`newName`) and tag (`newTag`). Replace the
  `ghcr.io/khorum-oss/...` placeholder with your registry.
- **Host**: each overlay patches the Ingress host — set your real hostnames.
- **Secrets**: `base/secret.yaml` holds placeholders. Supply real values via your secret manager; a
  secret-operator integration (external-secrets / 1Password) is a later slice.
- **Descriptor**: `base/kontinuance.yml` (mirrors `deploy/kontinuance.yml`) is served as a ConfigMap.
  Replace it with your pipeline.

## GitOps + promotion

See [`../argocd/README.md`](../argocd/README.md) for registering the ArgoCD Applications (stage
auto-sync, prod gated) and [`../pipeline/`](../pipeline/) for the `release.sh` / `promote.sh` flow.

## Not included (later slices)

Secret-operator integration, multi-replica + a shared run-store backend, authentication, and Helm
packaging.
