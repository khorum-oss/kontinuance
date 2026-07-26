# Feature Specification: Coverage Class-Level Drilldown (real Kover)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User: "coverage breakdown." The Coverage screen shows real per-**module** rows from the Kover
report, but clicking a module shows a placeholder ("class-level breakdown … arrives with real Kover data").
This closes the last fake-data gap: the server now derives **per-class** coverage from the same Kover XML,
and the UI drilldown shows a module's real classes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Drill into a module's classes (Priority: P1)

An operator opens Coverage, clicks a module, and sees its **classes** with real line/branch coverage and
missed lines — worst-covered first — instead of a placeholder.

**Why this priority**: The Coverage screen was the last place showing stubbed data. Real class coverage makes
the drilldown actually useful for finding gaps.

**Independent Test**: With a Kover report, open Coverage, click a module, and see its class rows with real
percentages.

**Acceptance Scenarios**:

1. **Given** a module row, **When** the operator clicks it, **Then** the screen lists that module's classes
   with real line %, branch %, and missed-line counts.
2. **Given** the class list, **When** it renders, **Then** the worst-covered classes (most missed lines)
   appear first.

---

### User Story 2 - Honest when a module has no class data (Priority: P2)

If a module has no per-class data (an old/fixture response, or a report without classes), the drilldown says
so plainly rather than showing a stale placeholder.

**Why this priority**: The point of "real data" is trust; an honest empty state beats an evergreen "coming
soon".

**Independent Test**: Drill into a module with no classes and see an explicit empty state.

**Acceptance Scenarios**:

1. **Given** a module with no class breakdown, **When** the operator drills in, **Then** an honest "no
   class-level data" message is shown.

### Edge Cases

- **Class display name**: shown as the path after the module (dot-joined, e.g. `model.Step`) so classes are
  legible and distinct across sub-packages.
- **No report / unparseable**: unchanged — the endpoint still falls back to fixture data (no classes), and
  the drilldown shows the honest empty state.
- **Response shape preserved otherwise**: only an optional `classes` array is added per module; the totals,
  module rows, and class count are unchanged.

## Requirements *(mandatory)*

- **FR-001**: The `/api/coverage` module entries MUST carry an optional per-class breakdown — each class's
  line %, branch %, and missed lines — derived from the same Kover XML.
- **FR-002**: The Coverage drilldown MUST render the selected module's real classes (worst-covered first);
  a module with no class data MUST show an honest empty state.
- **FR-003**: Class names MUST be displayed legibly (the path after the module, dot-joined).
- **FR-004**: The change MUST be additive to the `/api/coverage` shape (only an optional `classes` array per
  module); totals / module rows / class count are unchanged; no new dependency (the JDK DOM parser already
  reads the report, XXE-safe).

## Success Criteria *(mandatory)*

- **SC-001**: Drilling into a module shows its real class coverage rows, worst-covered first.
- **SC-002**: A module with no class data shows an honest empty state (no placeholder).
- **SC-003**: The `/api/coverage` totals/module rows are unchanged; only an optional `classes` array is added.
- **SC-004**: No new dependency; existing suites stay green.

## Assumptions

- **Same Kover XML, one more level.** The JaCoCo-format report already lists `<class>` elements with their
  own `<counter>`s inside each `<package>`; the reader now folds those into the module they belong to. No new
  source, no new dependency.
- **Module coverage stays package-derived.** Module line/branch percentages continue to come from the
  package counters (unchanged); the class rows are an additive breakdown.
