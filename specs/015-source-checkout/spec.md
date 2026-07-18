# Feature Specification: Source Checkout & Shared Workspace

**Feature Branch**: `claude/source-checkout`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "The core missing feature: a way for a pipeline to check out the code it runs against, and a workspace the steps share. Support it from both the YAML descriptor and the Kotlin DSL."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Steps share one workspace (Priority: P1)

A pipeline author writes multiple steps that build on each other — one produces files (a checkout, a
generated artifact), later steps consume them — and they all operate on the **same** working directory.

**Why this priority**: Without a shared workspace, a checkout in one step is invisible to the next, so no
real build is possible. This is the foundation the checkout step stands on.

**Independent Test**: A two-step pipeline where step 1 writes a file and step 2 reads it succeeds (the
file is visible), and the workspace is removed when the run ends.

**Acceptance Scenarios**:

1. **Given** a run with several steps, **When** a step creates a file at a relative path, **Then** a later
   step in the same run sees that file at the same path.
2. **Given** a completed run, **When** it ends (success, failure, or cancellation), **Then** its workspace
   directory is removed (no leftover directories).
3. **Given** a step with a `workingDir` sub-path, **When** it runs, **Then** that sub-path resolves
   **inside** the run's workspace and cannot escape to the host.

---

### User Story 2 - Check out a repo from the YAML descriptor (Priority: P1)

An author adds a checkout step to their `kontinuance.yml` that clones a repository (optionally at a
branch/tag) into the workspace, so the following steps build the checked-out code.

**Why this priority**: This is the headline capability — referencing the code the pipeline runs for.

**Independent Test**: A descriptor with a `git:` step parses to the checkout model; run against a
reachable repo, the workspace afterwards contains the repo's files.

**Acceptance Scenarios**:

1. **Given** a step declaring `git: { url, ref, dir }`, **When** the descriptor is parsed, **Then** it maps
   to the checkout step model with those values (unknown keys rejected by the strict parser).
2. **Given** a checkout step followed by a build step, **When** the run executes, **Then** the build step
   operates on the checked-out source in the shared workspace.

---

### User Story 3 - Check out a repo from the Kotlin DSL (Priority: P1)

The same checkout is expressible in the Kotlin DSL, producing the identical model as the YAML — so both
front-ends stay equivalent (Constitution Principle I).

**Why this priority**: The user maintains DSL pipelines; the DSL must express the checkout too.

**Independent Test**: A `gitStep { … }` in a `pipeline { }` builds the same checkout model the equivalent
`git:` YAML produces.

**Acceptance Scenarios**:

1. **Given** a `gitStep` in the DSL with a url/ref/dir, **When** the pipeline is built, **Then** it
   contains the same checkout step model as the equivalent descriptor.

### Edge Cases

- What happens when the checkout target directory is not empty (e.g. cloning into `.` after prior steps
  wrote there)? The clone fails as a normal step failure (naming the step) — the guidance is to put the
  checkout first, or clone into a sub-directory.
- What happens on an unreachable repo or a missing `git` binary? The step FAILS (naming the step), not an
  engine crash — the shared process-launch handling surfaces it.
- What happens to secret masking / environment scoping under a shared workspace? Unchanged: secrets are
  still resolved and masked per step, and the scoped environment is still per step. Only the working
  directory is now shared across a run.
- What happens across two concurrent runs? Each run has its own workspace; they never share.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A run MUST execute its steps against a **single shared workspace directory** created at run
  start and removed when the run reaches a terminal (or paused) end — replacing the previous per-step
  ephemeral directory. Files a step writes at a relative path MUST be visible to later steps in the run.
- **FR-002**: The workspace MUST remain isolated from the host: a step's `workingDir` sub-path MUST resolve
  inside the workspace and MUST NOT escape it; the workspace MUST be removed on run end.
- **FR-003**: Secret resolution/masking and environment scoping MUST remain **per step** (unchanged); only
  the working directory becomes shared.
- **FR-004**: The system MUST provide a **checkout step type** that clones a repository into the workspace,
  supporting a repository URL, an optional ref (branch/tag), an optional target sub-directory, and an
  optional shallow depth. A missing tool or unreachable repo MUST surface as a failed step.
- **FR-005**: The checkout step MUST be expressible in the **YAML descriptor** (a `git:` step key) under
  the strict parser (unknown keys rejected, exactly one step-type key per step).
- **FR-006**: The checkout step MUST be expressible in the **Kotlin DSL** and MUST produce the **same**
  model as the equivalent YAML (Principle I).
- **FR-007**: Two concurrent runs MUST NOT share a workspace.

### Key Entities *(include if feature involves data)*

- **Workspace**: the per-run shared, host-isolated directory the steps operate in.
- **Checkout step**: a step type describing a repository clone (url, ref, dir, depth) into the workspace.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A multi-step run where one step writes a file and a later step reads it succeeds.
- **SC-002**: No run workspace directory is left behind after a run ends.
- **SC-003**: The same checkout is authored in YAML (`git:`) and in the DSL (`gitStep`) and yields an
  identical pipeline model.
- **SC-004**: A checkout step followed by a build step lets the build operate on the checked-out source.
- **SC-005**: An unreachable repo / missing `git` ends the run as a failed step (no engine crash).

## Assumptions

- **Model evolution**: this intentionally changes the v0 per-step working-directory isolation
  (feature 001) to a per-run shared workspace — the change needed to build real code. The security
  properties that mattered (host isolation, cleanup, secret masking, environment scoping) are preserved.
- The checkout MVP supports **branch/tag refs** (via a shallow, branch-scoped clone); checking out an
  arbitrary commit SHA is a follow-up.
- **Resume interaction**: the workspace lives for a single run *invocation*. A run resumed after a durable
  approval gate is a fresh invocation with a fresh (empty) workspace — filesystem output from stages
  skipped as "already completed" is **not** restored. So keep post-gate steps self-sufficient (e.g.
  re-check out after the gate if the deploy needs the source). Persisting/restoring a workspace across a
  gate is a later feature.
- Credentials for private repos are supplied the existing way — as environment secrets referenced by the
  step (e.g. a token embedded in the URL or a `git` credential helper the operator configures); a
  first-class credential model is out of scope here.
- A repo/pipeline **configuration UI** ("configure a repo for first setup") and editing `kontinuance.yml`
  in the app are separate, later features; this feature is the engine capability, usable today via the
  descriptor the server already runs.
- `git` is available on the PATH where the engine runs.
