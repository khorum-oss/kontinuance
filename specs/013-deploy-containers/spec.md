# Feature Specification: Containerized Deployment — Local Compose (P1)

**Feature Branch**: `claude/deploy-containers`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "Containerize Kontinuance and provide a one-command local run — the P1 slice of aligning the deployment story with the khorum/relikquary `deploy/` layout. Container + compose artifacts only; no engine/server/web application code changes."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One command brings up the whole app on one origin (Priority: P1)

An operator clones the repo and runs a single compose command. It builds the server and web images from
source and starts them so the full application — UI, API, live stream — is reachable at one URL, with run
history persisted across restarts.

**Why this priority**: This is the entire point of the P1 slice — turning a two-part build-it-yourself
project into a runnable, self-contained stack. Everything else in this feature exists to make this work.

**Independent Test**: Run the compose stack; open the published URL; the runs list loads, a run can be
triggered, and a gated run can be approved — all from the browser, with no extra services.

**Acceptance Scenarios**:

1. **Given** a checkout with a container engine, **When** the operator runs the documented compose
   up command, **Then** the server and web images build from source and both containers start healthy.
2. **Given** the running stack, **When** the operator opens the published URL, **Then** the UI loads and
   its API + live-stream calls succeed on that same origin (no cross-origin setup).
3. **Given** a run has been recorded, **When** the stack is stopped and started again, **Then** the run
   history is still present (persisted in a named volume).

---

### User Story 2 - Configure the stack without editing images (Priority: P2)

An operator points the stack at their own pipeline descriptor and supplies pipeline secrets, using
environment variables and a copyable example env file — without rebuilding or editing the images.

**Why this priority**: A stack that only runs a baked-in demo is not operable; configuration via env +
mounts is what makes it usable, but it builds on US1 being runnable first.

**Independent Test**: Set the descriptor path and a secret via the env file, bring the stack up, and
trigger a run that uses that descriptor and secret.

**Acceptance Scenarios**:

1. **Given** the example env file copied and filled with placeholder values, **When** the stack starts,
   **Then** it uses those values (published port, run-store location, descriptor path, backend target).
2. **Given** a pipeline descriptor provided to the stack, **When** a run is triggered, **Then** the run
   uses that descriptor; pipeline secrets are read from environment variables, never from the image.

---

### User Story 3 - A local iteration override (Priority: P3)

A developer uses a thin override to iterate locally — e.g. exposing the server directly and pointing at a
working-copy descriptor — without changing the base compose file.

**Why this priority**: Convenience for contributors; not required to run the product, so lowest priority.

**Independent Test**: Bring the stack up with the override applied and confirm the override's effect
(e.g. the server port is reachable directly) without touching the base file.

**Acceptance Scenarios**:

1. **Given** the base compose file and the dev override, **When** both are applied, **Then** the
   override's local-iteration settings take effect and the base file is unmodified.

### Edge Cases

- What happens when the configured descriptor is missing/invalid in the container? The app behaves as
  documented (trigger rejected; config screen falls back to fixtures) — the container does not crash.
- What happens on `docker compose config`? The composition validates (no interpolation or schema errors)
  even before anything is built.
- What happens to the run store on `down` without volume removal? It persists; on `down -v` it is removed.
- What happens if a referenced pipeline secret env var is unset? The run fails fast (engine behavior),
  visible in the UI — not a container failure.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST provide a container image definition for the server that builds it from
  source and runs it as a non-root user, listening inside the container on the standard port and bindable
  on all container interfaces.
- **FR-002**: The project MUST provide a container image definition for the web UI that builds the static
  app from source and serves it, reverse-proxying the API, the live SSE stream, and the WebSocket to the
  server on one origin (SSE unbuffered; WebSocket upgraded).
- **FR-003**: The web image's proxy target MUST be configurable at container start via an environment
  variable (no rebuild to repoint at a different server address).
- **FR-004**: The project MUST provide a compose definition that brings up the server + web together so
  the whole application is reachable at a single published URL.
- **FR-005**: Run history MUST persist across container restarts via a named volume.
- **FR-006**: The configured pipeline descriptor MUST be supplied to the running stack without rebuilding
  the image (mounted/overridable), defaulting to the project's example descriptor.
- **FR-007**: Pipeline secrets MUST be supplied as environment variables to the running stack and MUST
  NOT be baked into any image; no real secret values are committed (placeholder names only).
- **FR-008**: The project MUST provide an example environment file enumerating the stack's settings
  (published port, run-store location, descriptor path, proxy target) with placeholder values.
- **FR-009**: The project MUST provide a thin local-iteration override to the base compose file that does
  not modify the base file.
- **FR-010**: The image build MUST keep dependency verification enabled (no weakening of the supply-chain
  gate) and MUST add no new build dependency.
- **FR-011**: The project MUST provide a short deploy README covering how to build and run the stack, and
  MUST state that Kubernetes/GitOps/promotion and authentication are later slices, not included here.
- **FR-012**: This feature MUST NOT change any engine, server, or web application code. The only
  non-`deploy/` addition is a build-context ignore file at the repository root.
- **FR-013**: No artifact MUST reference an external design source.

### Key Entities *(include if feature involves data)*

- **Server image**: a self-built, non-root runtime of the API server.
- **Web image**: a self-built static-UI server that fronts the API on one origin.
- **Compose stack**: the composition wiring server + web + a run-store volume into one reachable URL.
- **Environment file**: the copyable list of stack settings with placeholder values.
- **Run-store volume**: the named volume holding run history across restarts.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With a container engine installed, an operator can bring the whole application up with a
  single documented command and reach a working UI (runs list, trigger a run, approve a gated run) at one
  URL — no other services, no manual cross-origin wiring.
- **SC-002**: The compose definition validates cleanly (composition check passes) before anything runs.
- **SC-003**: Run history survives a stop/start of the stack (persisted volume).
- **SC-004**: The stack can be repointed at a different pipeline descriptor and given pipeline secrets
  using only the environment file / mounts — no image rebuild.
- **SC-005**: No engine/server/web application code changed; no new build dependency added; no secret
  values and no external design links committed.

## Assumptions

- The operator has a working container engine and compose tool; installing them is out of scope.
- The server is built with its existing distribution mechanism (no fat-jar); the image runs that
  distribution.
- Building images from source resolves dependencies from the project's public repositories with
  verification enabled; full image builds may be environment-dependent (network), while composition
  validation is always available — mirroring the project's existing "CI is the authoritative build" note.
- Authentication is out of scope (documented as a later slice); the single-origin compose stack is the
  supported local topology.
- Kubernetes, Kustomize, ArgoCD/GitOps, stage→prod promotion scripts, a combined single image, and a
  secret-operator integration are explicitly deferred to later slices.
- The run store remains file-backed and single-instance (consistent with the current durability model);
  scaling to multiple replicas is out of scope.
