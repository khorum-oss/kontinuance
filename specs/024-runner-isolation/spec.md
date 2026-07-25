# Feature Specification: Docker Runner Isolation (per-step image)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User picked **Runner isolation (Docker)** from the roadmap ("overview v1 item; when parallel/
multi-tenant runs matter"). Today every step runs directly on the host via `ProcessBuilder`. This feature
lets a step declare a **container image** it should run inside — the step's command executes in that image
with the run's workspace mounted, instead of on the host — so a step uses the image's toolchain and cannot
reach the host filesystem outside the workspace. Isolation is **opt-in per step**; steps without an image
run exactly as before.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run a step inside a container image (Priority: P1)

A pipeline author declares `image:` on a step; that step's command then runs inside the named container
image, with the run's workspace mounted as the working directory, so it uses the image's tools (a pinned
JDK/Gradle, Node, etc.) rather than whatever the host happens to have.

**Why this priority**: Reproducibility and isolation are the point of a CI runner. Running on the host
couples every run to the host's installed toolchain and lets a step touch the whole host filesystem.

**Independent Test**: A step with `image:` set produces a `docker run` invocation that mounts the workspace
and runs the step's command inside the image; a step without `image:` runs on the host unchanged.

**Acceptance Scenarios**:

1. **Given** a step declaring an image, **When** it runs, **Then** its command is launched inside that
   image with the run's workspace bind-mounted as the working directory.
2. **Given** a step with no image, **When** it runs, **Then** it runs on the host exactly as before (no
   behavior change).

---

### User Story 2 - Secrets reach the container without leaking (Priority: P1)

A step's declared secrets are available inside the container, but their **values never appear on the
command line** (so they are not exposed via process listings or command echo).

**Why this priority**: Secret masking (a core engine guarantee) must not be undermined by the isolation
mechanism.

**Independent Test**: For a step with an image and a declared secret, the container invocation forwards the
secret **by name only**; the secret value is not present anywhere in the launched argv.

**Acceptance Scenarios**:

1. **Given** a step with an image and a secret, **When** the container invocation is built, **Then** the
   secret is forwarded by name (its value is supplied out-of-band) and never placed on the argv.

### Edge Cases

- **Docker not installed / no daemon**: launching the container fails like any missing tool — a FAILED step
  naming `docker`, never an unhandled exception (the existing launch-failure handling covers it).
- **Same declaration in YAML and DSL**: `image:` is available both in the descriptor and the Kotlin DSL
  (v0 `step { }` and the typed `gradleStep`/`dockerStep`/… builders), producing identical models.
- **Non-process steps** (e.g. a manual-approval gate) ignore `image:` — there is no host command to isolate.
- **Blank image**: rejected at model construction (a step's image must be non-blank when present).

## Requirements *(mandatory)*

- **FR-001**: A step MUST be able to declare an optional container **image**; when set, the step's command
  MUST run inside that image (runner isolation) instead of on the host, with the run's workspace bind-mounted
  as the container working directory. When unset, the step MUST run on the host exactly as before.
- **FR-002**: The isolation MUST reuse the existing process machinery so an isolated step inherits the same
  timeout, streamed-and-masked output, cancellation, and process-tree cleanup as a host step.
- **FR-003**: A step's declared secrets MUST be forwarded into the container **by name only**; secret values
  MUST NOT appear on the launched command line. Host passthrough variables (PATH/HOME/…) MUST NOT be forced
  into the container.
- **FR-004**: `image:` MUST be accepted by the strict descriptor parser (step-level) and by the Kotlin DSL,
  producing identical models; an unknown key remains rejected.
- **FR-005**: The container runtime MUST be invoked as the host `docker` CLI (no new dependency); a missing
  `docker` binary MUST surface as a FAILED step naming `docker`, not an exception.

## Success Criteria *(mandatory)*

- **SC-001**: A step with an image yields a `docker run` invocation mounting the workspace and running the
  step's command inside the image; a step without one runs on the host unchanged.
- **SC-002**: An isolated step's secret is forwarded by name; its value never appears on the argv.
- **SC-003**: `image:` parses from YAML and is expressible in the DSL, with equal models.
- **SC-004**: No new dependency; the existing engine suites stay green; detekt/Kover pass.

## Assumptions

- **MVP scope is the `docker run` wrapper.** The real container run needs a Docker daemon on the host and is
  exercised in CI / on real hosts; this feature's argv construction is pure and unit-tested, and the seam is
  a `StepSandbox` so a Kubernetes/other backend can replace `docker` later without touching the executors.
- **The runner backend is engine-wide and per-step-gated.** A single sandbox is wired into every process
  executor; it wraps a step only when the step names an image, so isolation is opt-in and host steps are
  untouched. Deeper concerns (UID mapping to avoid root-owned workspace files, network policy, image pull
  policy, signal propagation to the container on kill) are documented follow-ups, not part of this MVP.
