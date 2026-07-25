# Feature Specification: Run-Derived Deploy (honest delivery view)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-23

**Status**: Draft

**Input**: User: "Real Pipeline/Deploy data." Pipeline is already served from each run's real persisted stages. Deploy was the last fixture. This makes `/api/deploy` a **delivery view derived from the latest real run**, and presents the genuinely-external bits (artifact registry, ArgoCD/cluster sync) honestly instead of fabricated data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the latest run's real delivery state (Priority: P1)

An operator opens the Deploy screen and sees the **latest run's** delivery: a SOURCE node plus a node for
each of the run's real stages with its real status, and a delivery panel showing how many stages completed,
the commit, and the run's state.

**Why this priority**: The Deploy screen was the last screen showing fabricated data (fake jars, pod counts,
sync revisions). Deriving it from the real run makes it trustworthy.

**Independent Test**: With a recorded run, open Deploy and see its stages as the promotion flow with real
statuses and a real stages-done/commit/state summary.

**Acceptance Scenarios**:

1. **Given** a recorded run, **When** Deploy loads, **Then** it shows a SOURCE node and a node per the run's
   stages, each carrying the stage's real status.
2. **Given** the run, **When** the delivery panel renders, **Then** it shows real stage-completion, the
   commit, and the run's state.

---

### User Story 2 - Be honest about what's external (Priority: P1)

Where Kontinuance genuinely has no data — the artifact registry and cluster/ArgoCD sync — the screen says so
plainly instead of showing fabricated artifacts and pod counts.

**Why this priority**: The point of "real data" is trust; showing invented external state is worse than
saying it's external.

**Independent Test**: Open Deploy and confirm the artifact manifest is an explicit "no registry integrated"
state and the panel notes ArgoCD/registry are external.

**Acceptance Scenarios**:

1. **Given** the Deploy screen, **When** it renders, **Then** the artifact manifest shows an honest empty
   state (Kontinuance runs no registry) and the delivery panel notes ArgoCD/registry are external.
2. **Given** no runs recorded, **When** Deploy loads, **Then** it shows an honest empty flow (no fabricated
   nodes).

### Edge Cases

- **No runs**: the flow is empty with a "no runs yet" message; the delivery panel reads "—" with the
  external note.
- **Old/partial runs**: a run without stages still yields a SOURCE node; the delivery panel counts what's
  present.
- **Statuses**: stage statuses map to a delivery vocabulary (synced/progressing/failed/awaiting/skipped/…)
  so they color correctly.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `/api/deploy` MUST derive its promotion flow from the **latest recorded run**: a SOURCE node
  plus one node per the run's real stages, each with the stage's real status.
- **FR-002**: The delivery panel MUST report real values from the run — stages completed, the commit, and
  the run's state — not fabricated pod/sync numbers.
- **FR-003**: The artifact manifest MUST be an honest empty state (Kontinuance runs no artifact registry),
  and the screen MUST note that the registry and ArgoCD/cluster sync are external.
- **FR-004**: With no runs, the endpoint MUST return an empty flow and the screen MUST show an honest empty
  state.
- **FR-005**: The change MUST NOT alter the `/api/deploy` response shape (nodes/artifacts/environment) so the
  web client is unchanged in contract; no new dependency.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a recorded run, Deploy shows a SOURCE node + a node per stage with the run's real statuses.
- **SC-002**: The delivery panel shows real stage-completion / commit / state from the run.
- **SC-003**: No fabricated artifacts or pod/sync numbers appear; the artifact manifest and external note are
  honest.
- **SC-004**: With no runs, the screen shows an empty, honest state.
- **SC-005**: No new dependency; the existing suites stay green.

## Assumptions

- **Deploy state is fundamentally external.** Kontinuance's real delivery signal is the run's own
  publish/deploy *stages*; the registry index and ArgoCD sync/health live out-of-band. This feature surfaces
  the real run-derived signal and is honest about the rest — a genuine ArgoCD/registry integration (querying
  sync/health and the registry) is a separate later feature.
- **Response shape is preserved** (nodes/artifacts/environment) so only the *source* changes, not the
  contract; the environment fields are repurposed to real run values (stages done / commit / state).
