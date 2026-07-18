# Contract: Deploy Topology (source of truth)

What the manifests/overlays/ArgoCD/scripts must express. Traces to feature 013's images and the server's
real config.

## Kubernetes objects (base, `namePrefix: kontinuance-`)

| Object | Key spec |
|---|---|
| ConfigMap `config` | `SERVER_ADDRESS=0.0.0.0`, `SERVER_PORT=8077`, `KONTINUANCE_STORE=/data/runs`, `KONTINUANCE_CONFIG_DESCRIPTOR=/etc/kontinuance/kontinuance.yml`, stream tuning, `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true` |
| ConfigMap `descriptor` (generated) | from `deploy/kontinuance.yml`; mounted RO at `/etc/kontinuance/` |
| Secret `secrets` | placeholder pipeline secrets (e.g. `DEPLOY_TOKEN`) — stringData, no real values |
| PVC `runs` | `ReadWriteOnce`, small (e.g. 1Gi) → `/data/runs` |
| Deployment `server` | image `kontinuance-server`; `replicas: 1`; `envFrom` config + secret; mounts PVC + descriptor; non-root securityContext; liveness `/actuator/health/liveness`, readiness `/actuator/health/readiness` on 8077 |
| Service `server` | ClusterIP, port 8077 |
| Deployment `web` | image `kontinuance-web`; env `KONTINUANCE_BACKEND=http://kontinuance-server:8077`; readiness `GET /` on 80 |
| Service `web` | ClusterIP, port 80 |
| Ingress | one host → `kontinuance-web:80` |

## Overlays

| Overlay | namespace | image `newTag` | host | replicas |
|---|---|---|---|---|
| stage | `kontinuance-stage` | `stage` | stage host | 1 |
| prod | `kontinuance-prod` | `stable` | prod host | 1 (single-writer) |

## ArgoCD

| Manifest | Policy |
|---|---|
| AppProject `kontinuance` | scopes the repo source + the two destination namespaces |
| Application `kontinuance-stage` | path `deploy/k8s/overlays/stage`; `syncPolicy.automated` (prune + selfHeal) |
| Application `kontinuance-prod` | path `deploy/k8s/overlays/prod`; **no** automated policy (manual sync) |

## Scripts

| Script | Effect |
|---|---|
| `release.sh` | build + push `kontinuance-server`/`-web` to `$REGISTRY`; set stage overlay image `newTag` to the built tag; (operator commits) |
| `promote.sh` | set prod overlay image `newTag` = stage overlay's current tag; (operator commits + `argocd app sync kontinuance-prod`) |

## CI

`.github/workflows/deploy-images.yml` — on `deploy/**` push/PR + `workflow_dispatch`: `docker build` both
images from source (no push); verification enabled in the server build.
