# Feature Specification: Runs List Filters & Search

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-27

**Status**: Draft

**Input**: User: "Runs list filters/search." The runs list shows every recent run with no way to narrow it —
as runs accumulate, finding a specific one (a failed run, a repo's runs, a commit) means scrolling. This adds
**filter controls** (status, trigger) and a **search box** (id / pipeline / repo / commit) over the runs list,
narrowing the live list in place.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Filter by status and trigger (Priority: P1)

An operator narrows the runs list to just the runs they care about — e.g. only **failed** runs, or only runs
triggered by a **pull request** — using filter controls above the list.

**Why this priority**: "Show me what failed" and "show me the PR checks" are the two most common ways an
operator slices a CI history; without them the list is a scroll.

**Independent Test**: With runs of mixed status/trigger, selecting a status (or trigger) shows only the
matching runs.

**Acceptance Scenarios**:

1. **Given** runs of mixed status, **When** the operator selects a status filter, **Then** only runs of that
   status remain visible.
2. **Given** runs from different triggers, **When** the operator selects a trigger filter, **Then** only runs
   from that trigger remain visible.
3. **Given** an active filter, **When** the operator resets it to "all", **Then** the full list returns.

---

### User Story 2 - Search by id, pipeline, repo, or commit (Priority: P1)

An operator types into a search box and the list narrows to runs whose id, pipeline, repository, or commit
matches — case-insensitive, substring.

**Why this priority**: Finding one specific run (a run id from a link, a commit under investigation) is a
frequent, direct need.

**Independent Test**: Typing part of a run's commit (or id/repo/pipeline) narrows the list to matching runs.

**Acceptance Scenarios**:

1. **Given** the runs list, **When** the operator types a substring of a run's id / pipeline / repo / commit,
   **Then** only runs matching that text remain.
2. **Given** a search with no matches, **When** it is applied, **Then** an honest "no runs match" state is
   shown (distinct from "no runs recorded yet").

---

### User Story 3 - Filters compose and coexist with live updates (Priority: P2)

Filters and search combine (all must match), and the live stream keeps working underneath — a newly-arriving
run that matches the active filter appears; the filter never drops runs from the underlying set, only from the
view.

**Why this priority**: Filtering must not fight the live runs feed or lose data; clearing a filter must be
instant.

**Independent Test**: With a filter applied, clearing it immediately restores every run (including any that
streamed in while filtered).

**Acceptance Scenarios**:

1. **Given** a status filter and a search term, **When** both are set, **Then** only runs matching **both**
   remain.
2. **Given** an active filter, **When** a matching run streams in, **Then** it appears; **When** the filter is
   cleared, **Then** all runs (including non-matching ones) are shown.
3. **Given** filters are applied, **Then** a count shows how many of the total runs are visible.

### Edge Cases

- **Blank search**: whitespace-only search matches everything (no accidental empty list).
- **Status normalization**: the filter matches the run's canonical status (the same normalization the row uses),
  so `Failed`, `Failed(step)`, etc. all match "failed".
- **Trigger values**: the trigger facet distinguishes UI-manual runs (`manual`) from event-source runs
  (`push` / `pull_request`), matched case-insensitively.
- **Missing fields**: a run with no repo/commit still matches an empty search and its status/trigger filters;
  search simply skips absent fields.

## Requirements *(mandatory)*

- **FR-001**: The runs list MUST offer a **status** filter and a **trigger** filter, each defaulting to "all",
  that narrow the visible runs to those matching the selected value (status matched by canonical status).
- **FR-002**: The runs list MUST offer a **search** box that narrows to runs whose id, pipeline, repository, or
  commit contains the entered text (case-insensitive substring); blank/whitespace matches all.
- **FR-003**: Filters and search MUST compose (a run is shown only if it matches all active criteria) and MUST
  operate over the already-loaded, live-updated run set — clearing them restores the full list immediately.
- **FR-004**: The list MUST show how many runs are visible out of the total, and MUST show an honest "no runs
  match" state when filters exclude everything (distinct from the "no runs recorded yet" empty state).
- **FR-005**: The change MUST be client-side only over the existing runs data (no new endpoint, no new
  dependency); the live runs stream and the run records are unchanged.

## Success Criteria *(mandatory)*

- **SC-001**: An operator can narrow the runs list to a status, a trigger, or a search term, and combine them.
- **SC-002**: Clearing filters instantly restores the full (live) list.
- **SC-003**: An over-filtered list shows a clear "no runs match" state and a visible count.
- **SC-004**: No new endpoint or dependency; the runs stream/records are unchanged; existing suites stay green.

## Assumptions

- **Client-side over the live set.** Every recent run already lives in the browser (seeded by the runs fetch,
  kept current by the SSE stream). Filtering is a projection over that in-memory set, so it is instant,
  composes with the live feed, and needs no server change — the runs stream takes no parameters and pushes
  per-run upserts, which server-side filtering would fight for no benefit at this (homelab) scale.
- **Facets from the data.** Status options are the canonical run statuses; trigger options are `manual`,
  `push`, and `pull_request` (the values runs actually carry). Pipeline and repo are covered by the free-text
  search rather than separate dropdowns, to keep the bar compact.
- **View-only.** Filtering never mutates or drops the underlying run set; it only changes what the table
  renders.
