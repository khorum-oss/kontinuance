# Feature Specification: Web UI Refresh — Repo Workspace (first-run repo config)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-23

**Status**: Draft

**Input**: User: "Update the UI based on the imported design (Kontinuance.dc.html) — refresh the screens, keeping them wired to the real APIs." This is the first slice: the design's login **step 2 → a full-screen repo workspace** (first-run repo configuration), replacing the small demo repo-picker. The other screens' refinements follow.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse and enter a repo setup (Priority: P1)

After signing in (or in open mode), the operator lands on a full-screen **repo workspace**: a header with
their identity + sign-out, provider filters, a card/list layout toggle, and a grid of repo setups each
tagged with its provider and whether it is configured. Clicking a repo enters mission control.

**Why this priority**: This replaces the placeholder 3-item repo picker with the design's real first-run
repo experience — a long-standing roadmap gap ("repo-setup redesign", "configure a repo for first setup").

**Independent Test**: Sign in; see the repo workspace with the seeded repos, provider filters, and layout
toggle; click a repo and land in the app.

**Acceptance Scenarios**:

1. **Given** the repo workspace, **When** it loads, **Then** the repo setups are shown with provider and
   configured/available badges, and a running count in the footer.
2. **Given** the repo workspace, **When** the operator clicks a repo, **Then** they enter mission control.
3. **Given** a provider filter, **When** the operator selects it, **Then** only that provider's repos show;
   the layout toggle switches the grid between cards and a list.

---

### User Story 2 - Add a repo (Priority: P2)

The operator opens **+ ADD REPO**, picks a source (GitHub / GitLab / a git URL), enters an org/repo or URL
and an optional branch, and adds it — the new repo appears at the top and is remembered per browser.

**Why this priority**: "Configure a repo for first setup" — the ability to add a repo is the point of the
workspace. Persisted locally now; a server-backed repo config is a later feature.

**Independent Test**: Open the add panel, enter a URL, add it, and see the repo appear; reload and it is
still there.

**Acceptance Scenarios**:

1. **Given** the add-repo panel, **When** the operator submits a URL, **Then** a repo derived from it is
   added to the top of the list and the panel closes.
2. **Given** an added repo, **When** the operator reloads, **Then** it persists (per-browser).

### Edge Cases

- **Auth unchanged**: sign-in still calls the real server (016/017); the workspace only changes the post-auth
  step, not the credential flow. EXIT still returns here; SIGN OUT ends the session.
- **Empty URL**: the add action is disabled until a URL is entered.
- **Open mode**: with no server auth, the workspace is reached directly (no sign-in), same as before.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: After authentication (or in open mode), the UI MUST present a full-screen repo workspace matching
  the imported design: header (identity + sign-out), provider filters with counts, a card/list layout toggle,
  a repo grid, and a footer count.
- **FR-002**: Each repo MUST show its provider and whether it is configured/available; clicking a repo MUST
  enter mission control.
- **FR-003**: Provider filters MUST filter the list; the layout toggle MUST switch card/list presentation.
- **FR-004**: The operator MUST be able to add a repo (GitHub / GitLab / git URL + optional branch); the added
  repo MUST appear at the top and persist per-browser.
- **FR-005**: The existing auth flow (sign-in, EXIT → workspace, SIGN OUT) MUST be preserved unchanged.
- **FR-006**: The change MUST introduce no new dependency and require no server change (local UI + persistence).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The repo workspace renders the design's header/filters/toggle/grid/footer and reads legibly in
  both themes.
- **SC-002**: Filtering and the layout toggle change the visible set/presentation.
- **SC-003**: Adding a repo shows it immediately and it survives a reload.
- **SC-004**: Clicking a repo enters the app; sign-in, EXIT, and SIGN OUT behave exactly as before.
- **SC-005**: No new dependency; the existing unit + E2E suites stay green.

## Assumptions

- **This is slice 1 of the design refresh.** The other screens (Runs progress column, Pipeline stage-flow +
  telemetry, Deploy, Coverage drill-down, Config syntax view) are refreshed in follow-up PRs, each keeping its
  live API wiring.
- **Repo config is local for now** (browser storage). A server-backed repo registry (list/add/scan
  kontinuance.yml, and driving runs per repo) is a separate later feature; today the selected repo is the
  entry gesture, and the server still runs its configured descriptor.
- The imported design is presentational (simulated data); the implementation keeps the real auth wiring and
  uses local state for the new repo list rather than the design's simulation.
