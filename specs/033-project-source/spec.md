# Feature Specification: Per-Project Source (repo/branch) Driving Checkout

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-26

**Status**: Draft

**Input**: User: "Per-project repo/branch." Projects exist (032) but a project is only a descriptor's text;
the repo to check out is buried inside the descriptor's `git:` step (`url`/`ref`). This gives a project a
**source** — a repo URL and an optional branch — set and edited in the UI, and drives the run's checkout from
it: a triggered run of a project checks out that project's source, overriding whatever the descriptor's
checkout would clone, and adding a checkout when the descriptor has none.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Give a project a source (Priority: P1)

An operator sets a project's **repo** and **branch** in the entry screen — when adding a project, and on an
existing project (including the seeded `default`) via an inline source editor — and sees the source on the
project's card.

**Why this priority**: The repo/branch is the thing an operator most wants to point at a project. Editing it
in the UI — not by hand-editing YAML — is the point of the feature.

**Independent Test**: Add a project with a repo + branch and see them on its card; edit an existing project's
source and see it update.

**Acceptance Scenarios**:

1. **Given** the add panel, **When** the operator supplies a repo (and optional branch) with the descriptor,
   **Then** the project is stored with that source and its card shows `repo · branch`.
2. **Given** an existing project, **When** the operator opens its inline source editor and saves a new
   repo/branch, **Then** the project's source updates and the card reflects it.
3. **Given** a project with no source, **When** its card renders, **Then** it says so plainly (the run uses the
   descriptor's checkout as-is).

---

### User Story 2 - A run checks out the project's source (Priority: P1)

When an operator triggers a run of the active project, the checkout uses the project's repo and branch instead
of whatever is written in the descriptor's `git:` step.

**Why this priority**: A source field that doesn't change what gets built is cosmetic. The value is that
selecting/activating a project decides the repo the run clones.

**Independent Test**: Activate a project whose source differs from the descriptor's `git:` step, trigger a run,
and confirm the run clones the project's repo/branch.

**Acceptance Scenarios**:

1. **Given** a project with a source and a descriptor that has a `git:` step, **When** a run is triggered,
   **Then** the checkout clones the project's repo (and its branch when set), not the descriptor's.
2. **Given** a project with a source and a descriptor with **no** `git:` step, **When** a run is triggered,
   **Then** a checkout of the project's source is added ahead of the pipeline's stages.
3. **Given** a project with **no** source, **When** a run is triggered, **Then** the pipeline runs exactly as
   the descriptor defines (unchanged behavior).

---

### User Story 3 - The run records its repo (Priority: P2)

A run triggered for a project with a source is stamped with that repo, so the runs list shows what it built.

**Why this priority**: Manual runs currently show no repo (only event-source runs do); stamping the project's
repo closes that gap for project-triggered runs.

**Independent Test**: Trigger a run of a project with a source and see the repo on the run record.

**Acceptance Scenarios**:

1. **Given** a project with a repo, **When** a run is triggered, **Then** the resulting run record carries that
   repo.

### Edge Cases

- **Blank source**: an empty/whitespace repo is treated as no source (the descriptor is used unchanged); the
  branch is ignored without a repo.
- **Branch without override target**: when a project sets a repo but no branch, the descriptor's `git:` step
  `ref` is preserved (only the URL is overridden); a synthesized checkout with no branch clones the default.
- **Multiple `git:` steps**: only the **first** checkout in the pipeline is overridden (the source checkout);
  later `git:` steps (e.g. fetching a second repo) are left alone.
- **Synthesized checkout naming**: the added checkout stage takes a name that does not collide with an existing
  stage.
- **Commit SHA**: the branch maps to the engine's `ref` (branch/tag); an arbitrary commit SHA is still not
  supported (unchanged from 015) and is out of scope here.

## Requirements *(mandatory)*

- **FR-001**: A project MUST be able to carry an optional **source**: a repo URL and an optional branch, stored
  alongside the project's descriptor and returned by `GET /api/projects` per project.
- **FR-002**: `POST /api/projects` MUST accept an optional repo/branch and store them as the new project's
  source; a blank repo is treated as no source.
- **FR-003**: The server MUST expose a way to set/update an existing project's source (repo/branch), rejecting
  an unknown project `404`.
- **FR-004**: A triggered run of the active project MUST apply that project's source to the checkout: override
  the first `git:` step's URL (and its ref when a branch is set), or add a checkout of the source ahead of the
  pipeline when the descriptor has no `git:` step; a project with no source leaves the pipeline unchanged.
- **FR-005**: A run triggered for a project with a repo MUST record that repo on its run record.
- **FR-006**: The entry screen MUST let the operator set a source when adding a project and edit an existing
  project's source inline, and MUST show each project's source (or an explicit "no source" state).
- **FR-007**: The change MUST introduce no new dependency and MUST NOT alter the descriptor grammar (the
  `git:` step keys are unchanged); the source is project metadata applied to the parsed pipeline, not a new
  descriptor key.

## Success Criteria *(mandatory)*

- **SC-001**: An operator can set and edit a project's repo/branch in the UI and see it on the card.
- **SC-002**: Triggering a run of a project with a source clones that repo/branch — overriding the descriptor's
  `git:` step, or adding a checkout when there is none.
- **SC-003**: A project with no source runs the descriptor exactly as before (no behavior change).
- **SC-004**: A project-triggered run records the project's repo.
- **SC-005**: No new dependency, no descriptor-grammar change; existing suites stay green.

## Assumptions

- **Source is project metadata, not a descriptor key.** The descriptor grammar (015 `git:` step) is unchanged;
  the source lives with the project (a sidecar next to its `<name>.yml`) and is applied to the in-memory
  pipeline at trigger time. This keeps the parser strict and the descriptor portable, and means a project
  without a source behaves exactly as today.
- **Override the first checkout; synthesize one when absent.** The first `git:` step is the source checkout;
  overriding its URL/ref (immutable `.copy`) is the least surprising way to "drive checkout from the project."
  When the descriptor has no checkout, a stage is prepended so the source is present for the following steps.
- **Branch maps to `ref`.** A project branch is the engine's branch/tag `ref`; commit-SHA checkout remains a
  separate follow-up (015).
- **Manual/project trigger scope.** This drives the manual trigger (the project picker's activate + RUN
  PIPELINE path). The GitHub event source carries its own repo/sha and is out of scope here.
