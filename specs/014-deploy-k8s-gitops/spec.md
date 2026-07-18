# Feature Specification: Kubernetes + GitOps Deployment (P2)

**Feature Branch**: `claude/deploy-k8s`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "P2 of aligning Kontinuance's deployment with relikquary: Kubernetes manifests (Kustomize base + stage/prod overlays), ArgoCD Applications (stage auto-sync, prod gated), and stage→prod promotion scripts. Deployment artifacts only — no engine/server/web application code changes. Deployment is driven from the operator's machine (no CI image-build job)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deploy to a cluster with Kustomize (Priority: P1)

An operator applies a Kustomize overlay to their cluster and gets a running Kontinuance — server + web on
one origin (via an Ingress), run history on a persistent volume, config from a ConfigMap, and pipeline
secrets from a Secret — for either a stage or a prod environment.

**Why this priority**: A cluster deployment is the core P2 outcome; everything else (GitOps, promotion)
automates *applying* what this produces.

**Independent Test**: Build an overlay (`kustomize build overlays/stage`) and apply it; the two workloads
become Ready, the Ingress serves the UI, and run history survives a pod restart.

**Acceptance Scenarios**:

1. **Given** the base + an overlay, **When** the overlay is built, **Then** it produces valid manifests
   for a namespaced server Deployment+Service, web Deployment+Service, an Ingress to the web on one
   origin, a run-store PVC, a ConfigMap, and a Secret — with the environment's namespace, image tag, and
   host applied.
2. **Given** the running workloads, **When** a pod restarts, **Then** run history persists (PVC) and the
   liveness/readiness probes report the server healthy.
3. **Given** the prod overlay, **When** it is built, **Then** it keeps a single server replica (the
   file-backed run store is single-writer) with production-appropriate image tag and host.

---

### User Story 2 - GitOps: stage auto-syncs, prod is gated (Priority: P2)

An operator registers two ArgoCD Applications. Stage continuously auto-syncs from git; prod only changes
on an explicit manual sync — a human gate before production.

**Why this priority**: This is the promotion *policy*; it depends on US1's overlays existing to point at.

**Independent Test**: The two Application manifests are valid; the stage one declares automated sync, the
prod one does not (manual sync only), each pointing at its overlay path.

**Acceptance Scenarios**:

1. **Given** the ArgoCD manifests, **When** they are inspected, **Then** stage has an automated sync
   policy and prod has none (manual), and both target the correct overlay path and destination namespace.
2. **Given** an AppProject, **When** it is inspected, **Then** it scopes the two Applications' sources and
   destinations.

---

### User Story 3 - Promote a tested image from stage to prod (Priority: P3)

An operator runs one script to build and publish images and pin the stage tag, and later a second script
to promote the stage-tested tag to prod — the promotion is the change ArgoCD then applies.

**Why this priority**: Convenience automation over US1/US2; the manual `kubectl`/git edits work without it.

**Independent Test**: The scripts are valid shell (`bash -n`, no `set -e` gaps), documented, and edit the
correct overlay fields.

**Acceptance Scenarios**:

1. **Given** the release script, **When** run with a registry, **Then** it builds + pushes both images and
   pins the stage overlay's image tag (a git change stage auto-sync picks up).
2. **Given** the promote script, **When** run, **Then** it sets the prod overlay's image tag to the
   stage-tested tag (a git change an operator then syncs to prod).

---

### Edge Cases

- What happens if the run-store PVC is `ReadWriteOnce` and replicas > 1? Not supported — prod stays a
  single replica (documented); scaling needs a shared run-store backend (a later slice).
- What happens when secrets are absent? The Secret carries placeholders; real values come from the
  operator (or a secret operator, a later slice) — documented, not committed.
- What happens on `kustomize build` of each overlay? It succeeds and yields namespaced, tagged manifests.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A Kustomize **base** MUST define the server Deployment+Service, the web Deployment+Service,
  an Ingress routing one host to the web (which fronts the API + stream + WebSocket), a run-store PVC, a
  ConfigMap of non-secret settings, and a Secret of placeholder pipeline secrets.
- **FR-002**: The server workload MUST run as non-root, read its run store from the PVC, read its pipeline
  descriptor from a mounted ConfigMap, take config from the ConfigMap/Secret, and declare liveness and
  readiness probes against the server's health endpoints.
- **FR-003**: The web workload MUST serve the UI and reach the server on one origin using the in-cluster
  server Service address (no rebuild to repoint).
- **FR-004**: **Stage** and **prod** Kustomize overlays MUST set their own namespace, image tag, and
  Ingress host; prod MUST keep a **single** server replica (single-writer run store).
- **FR-005**: Two **ArgoCD Application** manifests MUST target the stage and prod overlays; **stage MUST
  auto-sync** and **prod MUST require manual sync** (a production gate). An **AppProject** MUST scope them.
- **FR-006**: A **release** script MUST build + push both images and pin the stage overlay's image tag; a
  **promote** script MUST set the prod overlay's image tag to the stage-tested tag. Both MUST be valid,
  safe shell with a usage/help path.
- **FR-008**: No real secret values MUST be committed (placeholders only); no artifact MUST reference an
  external design source.
- **FR-009**: This feature MUST NOT change any engine, server, or web application code. Enabling the
  liveness/readiness probe endpoints MUST be done via deployment configuration (a ConfigMap-supplied
  property/env), not a source change.
- **FR-010**: A short README MUST document applying the overlays, registering the ArgoCD Applications, and
  the release/promote flow, and MUST state that a secret-operator integration and multi-replica scaling
  are later slices.

### Key Entities *(include if feature involves data)*

- **Kustomize base**: the shared, environment-agnostic manifest set.
- **Overlay (stage/prod)**: environment specialization (namespace, image tag, host, replicas).
- **ArgoCD Application/AppProject**: the GitOps sync policy (stage auto, prod gated) and its scope.
- **Promotion scripts**: release (build/push/pin stage) and promote (stage→prod tag).
- **CI image-build workflow**: automated Dockerfile build coverage.
- **Run-store PVC**: the persistent volume holding run history (single-writer).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Each overlay builds into valid, namespaced, correctly-tagged manifests (a build/dry-run of
  `overlays/stage` and `overlays/prod` succeeds).
- **SC-002**: The ArgoCD manifests express stage=auto-sync and prod=manual-sync, each pointing at the
  right overlay and namespace.
- **SC-003**: The promotion scripts are valid shell and edit only the intended overlay fields.
- **SC-005**: No engine/server/web application code changed; no secret values or external design links
  committed.

## Assumptions

- The operator has a cluster with an Ingress controller and (for GitOps) ArgoCD installed; installing
  those is out of scope.
- Images are published to a registry the operator controls; the manifests reference a placeholder
  registry/tag that overlays and the promote script set.
- The run store stays file-backed and single-writer, so prod runs a single replica (consistent with the
  durability model); multi-replica scaling and a shared run-store backend are later slices.
- Secrets are placeholders here; a secret-operator integration (e.g. an external-secrets/1Password
  operator) is a later slice.
- `kustomize`/`kubectl` and a container engine are available where these are applied/built; in the
  authoring sandbox, manifests are validated by `kustomize build` + YAML parsing and scripts by `bash -n`,
  with the cluster apply authoritative.
- Deployment is driven from the operator's machine (build/push/apply and `argocd sync`); there is no CI
  image-build job in this feature. The images are the P1 Dockerfiles (feature 013).
- This feature removes the `merge-main.yml` "Bump & Publish" workflow, so merges to `main` no longer
  auto-bump the version, publish the `dsl` module, or cut a release. Publishing/releasing becomes a
  deliberate machine-run step (the gradle publish task remains runnable locally). The PR-test workflow
  (`pr-main.yml`) is unchanged.
