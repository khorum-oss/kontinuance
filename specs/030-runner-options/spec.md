# Feature Specification: Runner Isolation Options (network / pull / uid)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User picked deepening **runner isolation** (the 024 follow-on). A step can already run inside a
container via `image:`; this adds per-step **runner options** that make real Docker runs practical:
**network policy** (e.g. cut the step off the network), **image pull policy**, and **user mapping** (run as
the host user so files the container writes into the mounted workspace aren't root-owned — otherwise they
break the host-side workspace cleanup). They apply only to a containerized step and go through the same
`StepSandbox` seam.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Constrain a containerized step's runner (Priority: P1)

A pipeline author sets `runner:` options on a step that has an `image:` — a network, a pull policy, and/or
user mapping — and the container is launched with those constraints.

**Why this priority**: Isolation isn't just "in a container" — a step that shouldn't touch the network, or
must not write root-owned files into the shared workspace, needs these knobs to be trustworthy.

**Independent Test**: A step with `image:` and `runner:` produces a `docker run` invocation carrying the
corresponding `--network` / `--pull` / `-u` flags.

**Acceptance Scenarios**:

1. **Given** a containerized step with `runner` options, **When** it runs, **Then** the container launches
   with `--network <net>`, `--pull <policy>`, and (when user mapping is on) `-u <hostuid>:<hostgid>`.
2. **Given** the same step in YAML and the DSL, **When** both are parsed, **Then** they produce identical
   models.

---

### User Story 2 - Options are gated and honest (Priority: P1)

Runner options apply only when the step names an image; setting them on a host step is rejected up front (so
you never think a step is isolated when it isn't). Where the host user can't be determined, user mapping is
a safe no-op rather than a broken flag.

**Why this priority**: A silent no-op here is a security surprise — `network: none` on a host step would
give **no** isolation. Failing fast prevents that; the uid no-op keeps the feature portable.

**Independent Test**: A `runner` block without an `image` is rejected; with user mapping requested but no
resolvable host uid, no `-u` flag is emitted.

**Acceptance Scenarios**:

1. **Given** a `runner` block on a step with no image, **When** it is parsed, **Then** it is rejected with a
   clear error.
2. **Given** user mapping requested but no resolvable host uid, **When** the container argv is built, **Then**
   no `-u` flag is added (the mapping is a safe no-op).

### Edge Cases

- **Unknown pull policy**: rejected (allowed: always/missing/never).
- **Unknown runner key / blank network**: rejected by the strict parser / model validation.
- **No runner options**: a containerized step's argv is exactly the 024 wrapper (behavior unchanged).

## Requirements *(mandatory)*

- **FR-001**: A step MUST accept optional `runner` options — **network**, **pull** policy, and **user
  mapping** — that apply only when the step names an `image`.
- **FR-002**: When set, the container MUST be launched with the corresponding flags: `--network <net>`,
  `--pull <policy>`, and (for user mapping) `-u <hostuid>:<hostgid>`.
- **FR-003**: Runner options on a step **without** an image MUST be rejected (they would silently run on the
  host with no isolation).
- **FR-004**: User mapping MUST be a safe no-op when the host uid/gid cannot be determined (portability); the
  argv construction MUST stay pure/unit-testable (the host user is supplied as data).
- **FR-005**: `runner` MUST be expressible in the descriptor (`runner: { network, pull, mapUser }`, strictly
  parsed) and the DSL, with identical models; no new dependency; a step without runner options is unchanged.

## Success Criteria *(mandatory)*

- **SC-001**: A containerized step's `runner` options add the right `--network`/`--pull`/`-u` flags.
- **SC-002**: Runner options without an image are rejected; an unknown pull policy / runner key is rejected.
- **SC-003**: User mapping is a no-op without a resolvable host uid.
- **SC-004**: A containerized step with no runner options is byte-for-byte the 024 wrapper; no new dependency.

## Assumptions

- **These are `docker run` flags**, so they only mean something inside a container — hence the image
  guardrail. The exact daemon semantics are Docker's; Kontinuance passes the flags through.
- **Host uid/gid is probed portably** (the owner of a freshly-created temp file — the process's ids on
  POSIX, null off POSIX) with no `com.sun`/native dependency, and injected into the sandbox as data so the
  argv construction stays deterministic and testable.
