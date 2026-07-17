# Feature Specification: Web UI (Mission-Control Dashboard)

**Feature Branch**: `009-web-ui` (specs dir only; work stays on `claude/kontinuance-cross-app-alignment-w3hk0o`)

**Created**: 2026-07-17

**Status**: Draft

**Input**: Build the Kontinuance web UI — a component-library-first "mission control" dashboard for the
CI/CD engine that lets an operator watch runs and pipelines live. It reads the existing server API
(health, runs list, run-by-id) plus live status over streaming, and presents seven screens behind a
persistent shell. Screens whose data the engine already records are wired to the real API; screens whose
data the engine does not yet expose are backed by new, typed stub endpoints so every screen is
data-connected and ready to switch to real sources later.

## User Scenarios & Testing *(mandatory)*

The maintainers have a running engine, a run history, and a live status stream, but no visual surface —
everything is CLI + JSON today. This feature gives operators a dashboard to observe the system: list and
open runs, watch a pipeline execute stage-by-stage, follow streamed logs, and review deploy state,
coverage, and the resolved pipeline config. It is **read-only observation** this feature; triggering and
approving are later work. Because the design is delivered as a reusable component library, each visual
element is independently reviewable in isolation before it is assembled into a screen.

### User Story 1 - See run history and open a run (Priority: P1)

An operator opens the dashboard and sees the most recent runs newest-first — status, identifier, source
ref, commit message, progress, duration, and age — and clicks one to open its detail.

**Why this priority**: This is the core observe loop and the only slice backed entirely by data the engine
already records (the runs list + run-by-id). It is the MVP: usable on its own with the real backend.

**Independent Test**: Point the UI at a server with recorded runs; the runs screen lists them newest-first
with correct status styling, and opening one shows that run's detail.

**Acceptance Scenarios**:

1. **Given** a server with several recorded runs, **When** the operator opens the runs screen, **Then**
   the runs appear newest-first with a status indicator, id, ref, message, progress, duration, and age.
2. **Given** the runs screen, **When** the operator selects a run, **Then** the run's detail screen opens
   showing that run's information.
3. **Given** a server with no runs, **When** the runs screen loads, **Then** it shows an empty state
   rather than an error.
4. **Given** the server is unreachable, **When** the runs screen loads, **Then** it shows a clear
   connection error and a retry affordance.

### User Story 2 - Watch a run live and follow its logs (Priority: P1)

An operator watches an in-progress run update in real time — status and progress change without a manual
refresh — and follows the streamed log output on the run's detail screen.

**Why this priority**: Live observation is the reason a dashboard beats re-running a CLI command; it builds
directly on the live stream the server already exposes and is essential to the "mission control" value.

**Independent Test**: With the live stream connected, record/append run activity on the server; the runs
list and the open run's detail reflect the change within a couple of seconds without a page reload.

**Acceptance Scenarios**:

1. **Given** the dashboard is open and the live stream is connected, **When** a new run is recorded,
   **Then** it appears at the top of the runs list automatically.
2. **Given** an open run detail, **When** new log/status activity arrives, **Then** the detail updates
   live and the log view follows the newest line.
3. **Given** the live stream drops, **When** connectivity is lost, **Then** the UI shows a degraded
   indicator and recovers (re-syncs) when the stream returns.

### User Story 3 - Explore pipeline, deploy, coverage, and config (Priority: P2)

An operator navigates the shell to inspect a run's pipeline as a stage/task flow with dependency tracing,
the deploy/promotion state with its artifact manifest and environment health, the coverage breakdown by
module, and the resolved pipeline configuration.

**Why this priority**: These screens complete the operator's picture and exercise the full component
library, but they present data the engine does not yet record — so they are backed by typed stub endpoints
this feature and become real when those data sources land. Valuable, but after the backed observe core.

**Independent Test**: Navigate to each of the four screens; each renders its content from its endpoint
(stub) with the correct visual states, and its components render in isolation from fixtures.

**Acceptance Scenarios**:

1. **Given** a selected run, **When** the operator opens the pipeline screen, **Then** stages and tasks
   render as a flow with per-task status/progress and dependency relationships shown on interaction.
2. **Given** the deploy screen, **When** it loads, **Then** the promotion nodes, artifact manifest, and
   environment health render with correct status styling.
3. **Given** the coverage screen, **When** it loads, **Then** line/branch/module coverage renders as a
   table with drill-down into a module, using the project's coverage tool's terminology (Kover).
4. **Given** the config screen, **When** it loads, **Then** the resolved pipeline configuration and a plan
   summary render.

### User Story 4 - Operator entry shell (Priority: P3)

An operator lands on a sign-in surface, then selects which repository setup to observe, and enters the
dashboard; a persistent sidebar and topbar provide navigation, a live system indicator, and identity.

**Why this priority**: It frames the product and navigation, but is a presentational shell this feature —
no real authentication or authorization is enforced yet — so it is the lowest priority.

**Independent Test**: The sign-in and repo-selection steps advance to the dashboard; the sidebar navigates
between screens and highlights the active one; the topbar shows the live indicators.

**Acceptance Scenarios**:

1. **Given** the entry shell, **When** the operator completes sign-in and picks a repo setup, **Then** the
   dashboard opens on the runs screen.
2. **Given** the dashboard, **When** the operator uses the sidebar, **Then** the active screen changes and
   the active nav item is highlighted.

### Edge Cases

- Server unreachable or a stub endpoint errors → the affected screen shows a clear error + retry, and the
  rest of the shell stays usable.
- Empty data (no runs, no coverage, no artifacts) → each screen shows a purposeful empty state.
- Live stream disconnect/reconnect → degraded indicator, then automatic re-sync without a reload.
- A run referenced in the URL does not exist → a not-found state on the detail screen.
- Very long logs / many runs → the views stay responsive and scroll within their own regions.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The UI MUST present a runs list, newest-first, showing each run's status, identifier, source
  ref, commit message, progress, duration, and age, sourced from the real runs API.
- **FR-002**: The UI MUST let the operator open a single run's detail from the list, sourced from the real
  run-by-id API.
- **FR-003**: The UI MUST reflect live run activity (new runs, status/progress/log changes) without a
  manual refresh, using the server's live stream, and MUST degrade gracefully and re-sync on disconnect.
- **FR-004**: The UI MUST provide pipeline, deploy, coverage, and config screens, each rendering from a
  typed endpoint; where the engine does not yet record that data, the endpoint is a **stub** with a stable
  typed contract so the screen can switch to a real source without UI change.
- **FR-005**: The coverage screen MUST use the project's own coverage tool (Kover) terminology and shape
  (line/branch/module), not a different tool's.
- **FR-006**: Every screen and shared visual element MUST exist as an isolated, independently viewable
  component driven by typed example data (a component library / stories), so each can be reviewed alone.
- **FR-007**: The UI MUST provide a persistent shell (sidebar navigation + topbar) that indicates the
  active screen, live system status, and operator identity, and an entry (sign-in → repo-setup) flow.
- **FR-008**: The UI MUST show purposeful empty, loading, and error states for every data-backed screen.
- **FR-009**: New server stub endpoints MUST keep the existing API contract unchanged and MUST preserve the
  server's supply-chain gate (dependency verification stays enabled).
- **FR-010**: The UI MUST NOT embed or reference any external design-source location; the visual design is
  reproduced as first-class UI code.

### Key Entities *(include if feature involves data)*

- **Run (summary)**: id, source ref (branch + commit), message, status, progress, duration, age — the row
  in the runs list (from the real API).
- **Run (detail)**: a run's full record plus its live log/status stream (from the real API).
- **Pipeline**: stages, each with tasks (name, tool, status, progress, dependencies) — stub contract.
- **Deploy**: promotion nodes, artifact manifest entries (kind, name, digest, state), environment health —
  stub contract.
- **Coverage**: line/branch totals + per-module coverage rows (Kover) — stub contract.
- **Config**: the resolved pipeline configuration text + a plan summary — stub contract.
- **Component library**: the set of shared visual elements (status dot, progress bar, tool badge, run row,
  stage card, coverage bar, log line, etc.) with example data.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can go from opening the dashboard to reading a specific run's detail in under 15
  seconds, with runs shown newest-first.
- **SC-002**: A newly recorded run appears in the UI within 3 seconds without a manual refresh.
- **SC-003**: 100% of the seven screens render with correct empty, loading, and error states.
- **SC-004**: 100% of shared visual elements are viewable in isolation from example data (a reviewer can
  open any component alone).
- **SC-005**: The runs, run-detail, and live screens read only the real API; the four forward-looking
  screens read only their typed endpoints — 0 hard-coded data baked into the wired screens.
- **SC-006**: Adding the stub endpoints leaves the existing API responses unchanged and the server's
  dependency-verification gate enabled (0 regressions to the established contract or supply-chain gate).
- **SC-007**: No external design-source location appears anywhere in the shipped code or docs.

## Assumptions

- The server read API (health, runs list newest-first + bounded, run-by-id) and a live run stream already
  exist and are the source of truth for the runs/detail/live screens.
- Pipeline, deploy, coverage, and config data are **not** yet recorded by the engine; this feature adds
  typed stub endpoints (fixture-backed) with contracts stable enough to later swap in real sources.
- Coverage is modeled on the project's actual coverage tool, Kover (line/branch/module), reading its report
  shape rather than inventing a different tool's.
- Authentication/authorization is **not** enforced this feature; the entry flow is a presentational shell.
- Write actions (triggering runs, approving promotions) are out of scope; the UI is read-only observation.
- The UI is a separate frontend app in the repository that talks to the server over HTTP + the live stream;
  it does not change the engine's public contract.
