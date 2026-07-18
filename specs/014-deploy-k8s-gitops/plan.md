# Implementation Plan: Kubernetes + GitOps Deployment (P2)

**Branch**: `claude/deploy-k8s` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-deploy-k8s-gitops/spec.md`

## Summary

Extend `deploy/` with the P2 layer that relikquary ships: a Kustomize base + stage/prod overlays, ArgoCD
Applications (stage auto-sync, prod gated) + an AppProject, `release.sh`/`promote.sh`, and a CI workflow
that builds the P1 images. Builds on the P1 images (feature 013, on `main`). No application code changes;
the liveness/readiness probe endpoints are enabled via a ConfigMap-supplied property, not a source edit.

## Technical Context

**Language/Version**: Kubernetes YAML (Kustomize), ArgoCD CRDs, POSIX/bash scripts, a GitHub Actions
workflow. Targets the JDK 21 server image + nginx web image from feature 013.

**Primary Dependencies**: Kustomize (kubectl built-in), ArgoCD (operator-installed), an Ingress
controller. No new application dependency.

**Storage**: A `ReadWriteOnce` PVC for the file-backed run store (single-writer → single replica).

**Testing**: `kustomize build overlays/{stage,prod}` (authoritative on a machine with kubectl);
`bash -n` on the scripts; YAML parse of every manifest. CI builds the images from source.

**Target Platform**: A Kubernetes cluster with Ingress + ArgoCD.

**Project Type**: Deployment/GitOps artifacts + CI for the existing web-service + static SPA.

**Constraints**: no application code change (probes enabled via ConfigMap env, not source); prod single
replica; no secrets committed (placeholders); no external design link; verification stays enabled in the
CI image build.

**Scale/Scope**: ~18–22 files under `deploy/k8s`, `deploy/argocd`, `deploy/pipeline`, one CI workflow.

## Constitution Check

- **I. Stable Public Contract**: PASS — deploys the existing server/UI unchanged; no contract change.
- **II. Test-First / Integration-Verified**: PASS (adapted) — no app behavior change; verification is
  `kustomize build` + `bash -n` + YAML parse + a CI image build (`quickstart.md`).
- **III. Quality Gates**: PASS — no Kotlin/TS changed; detekt/Kover/Sonar unaffected. The CI image build
  runs the real Gradle build with verification enabled.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain**: PASS — no new dependency; verification enabled in the image build; no secrets
  committed; nothing published by CI (build only).

No violations → Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/014-deploy-k8s-gitops/
├── plan.md · research.md · data-model.md · quickstart.md
├── contracts/deploy-topology.md    # the k8s objects, probes, sync policy source of truth
└── tasks.md                        # /speckit-tasks
```

### Source Code (repository root)

```text
deploy/
├── k8s/
│   ├── base/
│   │   ├── kustomization.yaml        # resources + namePrefix + labels + configMapGenerator(descriptor) + images
│   │   ├── configmap.yaml            # non-secret settings (+ MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED)
│   │   ├── secret.yaml               # placeholder pipeline secrets
│   │   ├── pvc.yaml                  # run-store (RWO)
│   │   ├── server-deployment.yaml    # non-root, probes, PVC + descriptor mounts
│   │   ├── server-service.yaml
│   │   ├── web-deployment.yaml       # KONTINUANCE_BACKEND = in-cluster server Service
│   │   ├── web-service.yaml
│   │   └── ingress.yaml              # one host → web
│   └── overlays/
│       ├── stage/kustomization.yaml  # namespace, tag=stage, host, 1 replica
│       └── prod/kustomization.yaml   # namespace, tag=stable, host, 1 replica (single-writer)
├── argocd/
│   ├── project.yaml                  # AppProject
│   ├── application-stage.yaml        # automated sync (prune + selfHeal)
│   ├── application-prod.yaml         # manual sync (gated)
│   └── README.md
├── pipeline/
│   ├── release.sh                    # build + push both images; pin stage tag
│   └── promote.sh                    # set prod tag = stage-tested tag
└── k8s/README.md                     # apply overlays + ArgoCD + promote flow
.github/workflows/deploy-images.yml   # CI: build both images on deploy/** changes
```

No changes under `engine/`, `server/`, `web/`, or `gradle/`.

**Structure Decision**: Additive to the existing `deploy/` (feature 013). The demo descriptor
(`deploy/kontinuance.yml`) is reused as the base's descriptor ConfigMap. Probes are enabled by setting
`MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true` in the ConfigMap (Spring relaxed binding), so
`/actuator/health/{liveness,readiness}` light up without touching `application.yml`.

## Complexity Tracking

> No Constitution Check violations — no entries.
