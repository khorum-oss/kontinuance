# Feature Specification: Project Auto-Registry & Session-Gated Dashboard

**Feature Branch**: `feat/039-project-auto-registry`

**Created**: 2026-08-16

**Status**: Draft

**Input**: User: "I would like for autoregistry in kontinuance ui, so that if it is properly authenticated,
you should see the project show up in the kontinuance dashboard. it should key off whatever project name" —
plus "I at least want some auth login for session, which will also kind of dictate what I can see."

Today a **project** (032) and a **run** (006) are unrelated concepts. A project is a stored descriptor plus an
`.active` pointer; a run record carries `pipeline`, `repo`, `sha`, and `trigger` but never says which project
it belongs to. The consequence, observed live on the stage deployment: the CI gate's `relikquary-pr`
runs are recorded and served by `/api/runs`, while `/api/projects` still returns only the seeded `default` —
so the dashboard can show a global run list but can never show *a project's* builds.

This feature makes projects appear **on their own**, derived from the runs the server already reads, keyed off
an optional `project:` name authored in the descriptor and falling back to the repository name. It also turns
on the existing authentication (016/017) for the deployment, so the dashboard is reached through a sign-in and
an unauthenticated visitor sees nothing but the login screen.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A project appears without anyone registering it (Priority: P1)

An operator wires a repository into the CI gate. Once its builds start landing, the project shows up in the
dashboard by itself — no "add project" step, no descriptor upload, no restart.

**Why this priority**: This is the headline capability and the reported gap. Registration that must be done by
hand is registration that does not happen; the run history already contains everything needed to know a
project exists.

**Independent Test**: With run records present for a repository that was never registered, list the projects
and see that repository's project, marked as derived, with its build count.

**Acceptance Scenarios**:

1. **Given** recorded runs carrying `repo: khorum-oss/relikquary` and no registered project of that name,
   **When** the operator lists projects, **Then** a `relikquary` project is present and flagged as derived.
2. **Given** those same runs, **When** the operator lists projects, **Then** the entry carries the number of
   runs, the most recent run's status, and when it ran.
3. **Given** no runs and no registered projects beyond the seeded `default`, **When** the operator lists
   projects, **Then** only `default` is returned (nothing is invented).
4. **Given** a derived project, **When** the operator lists projects again, **Then** nothing has been written
   to the project store — the entry is recomputed from the runs each time.

---

### User Story 2 - Scope the dashboard to one project (Priority: P1)

An operator selects a project and the dashboard narrows to that project's builds, so "what happened on
Relikquary" is one click rather than a scroll through every run on the server.

**Why this priority**: Auto-registration that only produces a name in a list does not deliver "see the build
info in the project". Selection has to actually scope the view, or the feature stops one step short of its
purpose.

**Independent Test**: With runs from two different projects recorded, select one and confirm the runs list
shows only that project's runs, and that an "all projects" choice restores the full list.

**Acceptance Scenarios**:

1. **Given** runs from more than one project, **When** the operator selects a project, **Then** the runs list
   shows only runs resolving to that project.
2. **Given** a scoped runs list, **When** the operator chooses "all projects", **Then** every run is shown
   again.
3. **Given** a selected project, **When** a new run for that project arrives on the live stream, **Then** it
   appears in the scoped list without a reload.
4. **Given** a selected project, **When** a new run for a *different* project arrives, **Then** it does not
   appear in the scoped list, and it is not lost — choosing "all projects" reveals it.

---

### User Story 3 - A derived project cannot be triggered, and says why (Priority: P1)

An operator selects a project that exists only because builds ran for it. It has no descriptor on the server,
so there is genuinely nothing to run — the dashboard says so plainly instead of offering a button that fails.

**Why this priority**: Derived projects break the registry's founding invariant (a project *is* a descriptor).
If the UI ignores that, RUN PIPELINE either errors or, worse, silently runs some other project's descriptor.

**Independent Test**: Select a derived project and confirm the trigger control is disabled and states the
reason; select a registered project and confirm it is enabled.

**Acceptance Scenarios**:

1. **Given** a derived project with no stored descriptor, **When** it is selected, **Then** the trigger control
   is disabled and shows a reason naming the project.
2. **Given** a derived project is selected, **When** the operator inspects the server's live descriptor,
   **Then** it is unchanged — activating a descriptor-less project never overwrites it.
3. **Given** a registered project with a stored descriptor, **When** it is selected, **Then** the trigger
   control is enabled and activation writes that descriptor as it does today.
4. **Given** a derived project, **When** a descriptor is registered under the same name, **Then** the entry
   becomes a normal registered project — runnable, no longer flagged derived — and keeps its run history.

---

### User Story 4 - Name the project explicitly in the descriptor (Priority: P2)

A pipeline author writes `project: relikquary` at the top of a descriptor so that the PR gate, the stage
delivery pipeline, and the prod promotion all report under one project instead of three.

**Why this priority**: The repository fallback covers the common case immediately, but one app legitimately
runs several pipelines. Without an explicit name they fragment, and a project can never be renamed or span
repositories.

**Independent Test**: Give two descriptors with different pipeline names the same `project:` value, run both,
and confirm a single project entry covering both runs.

**Acceptance Scenarios**:

1. **Given** a descriptor with `project: relikquary`, **When** it runs, **Then** the recorded run carries that
   project name.
2. **Given** two pipelines sharing one `project:` value, **When** both have run, **Then** the dashboard shows
   one project whose scoped runs list contains runs from both pipelines.
3. **Given** a descriptor with an unknown top-level key, **When** it is parsed, **Then** it is still rejected —
   adding `project` does not loosen strict parsing.
4. **Given** a descriptor with no `project:` key, **When** it runs against a repository, **Then** the project
   resolves from the repository name exactly as before the key existed.

---

### User Story 5 - Sign in before seeing anything (Priority: P2)

An operator reaching the dashboard is asked to sign in. Only after a successful sign-in do the projects, runs,
and controls appear; without a session the API answers nothing but the public endpoints.

**Why this priority**: The deployment currently runs open — the trigger, approve, and reject endpoints are
reachable by anyone who can reach the host, which the hub's own runbook flags as an accepted risk. Since the
enforcement mechanism already ships (016), the remaining work is deployment, and it belongs with the feature
that makes the dashboard worth reaching.

**Independent Test**: With credentials configured, call a protected endpoint without a session and observe a
rejection; sign in through the UI and observe the projects and runs.

**Acceptance Scenarios**:

1. **Given** both credentials are configured, **When** the dashboard is opened without a session, **Then** the
   login screen is shown and no project or run data is retrievable.
2. **Given** both credentials are configured, **When** the operator signs in successfully, **Then** the project
   picker lists every project — derived and registered alike — and the dashboard is usable.
3. **Given** a signed-in operator, **When** they sign out, **Then** protected endpoints reject again.
4. **Given** credentials are configured, **When** the CI event source records a run, **Then** it is unaffected
   by enforcement — it writes to the run store directly and never calls the API.

### Edge Cases

- **Legacy records.** Runs recorded before this feature have no `project` field. They resolve through the
  repository fallback, so an existing history lights up with no migration, no backfill, and no re-run.
- **Neither project nor repository.** A run with no `project:` and no repository (a local manual run) resolves
  to no project. It MUST remain visible in the unscoped runs list and MUST NOT be assigned an invented name;
  the pipeline name is deliberately *not* used as a fallback, since that would split one application into a
  project per pipeline.
- **Name collision.** A derived name equal to a registered project's name yields exactly one entry: the
  registered project, runnable, carrying the derived run statistics.
- **Unsafe derived names.** A repository name that is not a safe slug (the existing `[A-Za-z0-9._-]{1,64}`
  rule) MUST NOT become a project entry — derived names go into the `.active` file and a path variable, so an
  unsafe name is treated as unassigned rather than sanitized into something ambiguous.
- **A derived project's runs age out.** Because derived entries are computed, a project whose last run is
  removed simply stops being listed. If it was the active project, the server MUST behave as though no project
  is active rather than erroring.
- **Active pointer to nothing.** An `.active` name matching neither a registered nor a derived project MUST be
  treated as "no active project"; the dashboard falls back to the unscoped view.
- **Half-configured credentials.** Setting only a username or only a password leaves the server **open** with a
  startup warning (existing 016 behavior). A deployment MUST set both or neither — a partially-applied secret
  is the realistic way to believe the dashboard is protected when it is not.
- **Sessions and restarts.** Sessions are in-memory, so a server restart signs everyone out. Acceptable at one
  replica and one operator; the operator signs in again.

## Requirements *(mandatory)*

- **FR-001**: The descriptor MUST accept an **optional** top-level `project:` string key, and the strict parser
  MUST continue to reject unknown top-level keys. The Kotlin DSL MUST expose the same field.
- **FR-002**: A run record MUST carry an optional project name, populated from the pipeline's `project` when
  the run is recorded, and MUST round-trip through storage with the field absent when unset (records written
  before this feature MUST still load).
- **FR-003**: The project of a run MUST resolve by precedence: the run's explicit project, else the repository's
  short name (the segment after the final `/`), else none. A resolved name that is not a valid project name
  (the existing safe-slug rule) MUST resolve to none rather than being sanitized. Resolution MUST be one shared
  rule used everywhere a project is inferred.
- **FR-004**: Listing projects MUST return the union of registered projects and projects derived from the run
  store. Derived entries MUST NOT be written to the project store; they MUST be recomputed on each listing.
- **FR-005**: Each listed project MUST report whether it is derived, whether it is runnable (a stored descriptor
  exists), how many runs it has, and the status and time of its most recent run.
- **FR-006**: Activating a derived project MUST record it as active **without** writing the server's live
  descriptor file, and MUST succeed rather than reporting the project unknown. Activating a registered project
  MUST behave exactly as it does today. An active name matching neither a registered nor a derived project MUST
  be reported as no active project, never as an error.
- **FR-007**: A run's project MUST be exposed on the run payload the dashboard already consumes, and the runs
  list MUST be scopeable to the active project, with an explicit "all projects" view that restores every run.
  Scoping MUST compose with the existing status/trigger filters and search, and MUST NOT fight the live stream.
- **FR-008**: The dashboard MUST disable the trigger control for a project that is not runnable and MUST state
  the reason, naming the project.
- **FR-009**: A project that is not runnable MUST become runnable by registering a descriptor under the same
  name through the existing project-creation endpoint, with its run history intact.
- **FR-010**: When both operator credentials are configured, every endpoint except the auth, API-health, and
  actuator paths MUST require a valid session — including the runs stream and the WebSocket upgrade. This is
  existing behavior (016) and MUST remain true for the endpoints this feature adds or changes.
- **FR-011**: The change MUST add no new runtime dependency, and dependency verification MUST stay enabled.

## Success Criteria *(mandatory)*

- **SC-001**: A repository whose builds land in the run store appears as a project in the dashboard without any
  manual registration step.
- **SC-002**: The stage deployment shows its existing `relikquary` build history immediately after
  upgrade — no migration, no backfill, no re-run.
- **SC-003**: Selecting a project scopes the runs list to that project; "all projects" restores the full list;
  live runs continue to arrive in both views.
- **SC-004**: A project with no descriptor cannot be triggered, and the dashboard explains why rather than
  failing on click.
- **SC-005**: Two pipelines sharing one `project:` value report as a single project.
- **SC-006**: With credentials configured, an unauthenticated visitor can retrieve no project or run data; a
  signed-in operator sees everything.
- **SC-007**: Existing test suites stay green, no new dependency is introduced, and the CI event source is
  unaffected by authentication enforcement.

## Assumptions

- **Derivation, not push.** The server derives projects from the run records it already reads, rather than the
  CI event source calling an API to register them. On the target deployment the event source and the server
  share a filesystem (the server mounts the runner's run directory), so derivation needs no token, no network
  call inside a build, and no new failure mode when a registration call fails mid-run. A push-based
  registration API becomes worthwhile only when a runner shares no filesystem with the server; it is out of
  scope here and nothing in this design blocks it.
- **Computed, never persisted.** Derived entries are a projection over the run store. This keeps the project
  store's invariant intact — a file in it is a real registered descriptor — and makes the list self-healing: no
  stale entry can outlive the runs that produced it.
- **Repository fallback, not pipeline fallback.** Falling back to the pipeline name would turn one application
  with a PR gate, a delivery pipeline, and a promotion pipeline into three projects. The repository is the
  better proxy for "the thing being built"; an explicit `project:` covers everything the repository cannot.
- **Binary visibility.** One operator credential pair, as 016 already implements: signed in sees every project,
  signed out sees the login screen. Per-user project visibility would need a user store and an authorization
  filter on every read path (list, runs, run detail, logs, SSE, WebSocket) with a leak test for each; that is a
  separate feature, deliberately not started here.
- **Client-side scoping.** Every recent run is already in the browser, seeded by the runs fetch and kept current
  by the stream, and 037 established filtering as a projection over that set. Project scoping reuses that
  mechanism rather than adding a server-side query parameter, which would fight the parameterless stream for no
  benefit at homelab scale. A server-side filter remains available later if the store outgrows this.
- **Auth enablement is deployment work.** The enforcement mechanism, the session cookie, and the login UI all
  ship already (016/017). Turning them on is a matter of configuring both credentials from a secret created
  out-of-band. No authentication code is written by this feature.

## Rollout (deployment, non-code)

These steps live wherever the deployment is configured, not in this repository. They are recorded here
because the feature is not observable without them.

1. Add a `project:` key to the watched repository's PR descriptor and refresh the event source's checkout,
   so runs carry the explicit name rather than relying on the repository fallback.
2. Create the operator credential out-of-band and set **both** credential environment variables on the
   server; the deployment manifest must never carry the value itself.
3. Rebuild and ship both container images, then roll them out.
4. Verify in a browser: sign-in succeeds, the project picker lists the derived project with its build history,
   selecting it scopes the runs list, and the trigger control is disabled with its reason shown.
5. Update the deployment's readiness notes — this closes the standing "Kontinuance endpoints unauthenticated"
   caveat, demoting any external access policy from the only lock to defense in depth.

## Follow-up: a run must carry the project that started it (2026-09-08)

Shipped 039 resolved a run's project from the descriptor's `project:` key, else the repository's short name.
Neither is available for a run started from the dashboard's own trigger, and the *registered* project the
operator activated — the one thing that unambiguously answers "whose run is this?" — was never recorded. The
observable failure: a run started under project **P** disappeared from P's scoped runs list and reappeared
only under "all projects".

Three separate records lost the owner, so the run vanished at a different moment in each case:

- the immediate `Running` record the trigger writes — the run vanished for as long as it was in flight, then
  reappeared if the descriptor happened to declare `project:`;
- the terminal record, when the descriptor declares no `project:` and the project has no source repository —
  the run never appeared under its project at all;
- the record a resumed run writes after an approval gate, which dropped `repo` as well.

- **FR-012**: A run started by the dashboard trigger MUST record the project it was launched under, on every
  record it writes. The descriptor's own `project:` key still takes precedence (the 039 rule is unchanged);
  the activating project is the fallback the reader cannot infer, ahead of nothing at all. With no active
  project and no declared one, the record carries no project and the repository fallback still applies —
  no owner is invented.
- **FR-013**: A run paused at an approval gate MUST keep its repository and project when it resumes.

## Follow-up: the pipeline view must describe the run on screen (2026-09-08)

The Pipeline screen (009) contradicted the rest of the dashboard in two ways, both fixed here:

- **FR-014**: `GET /api/runs/{id}/pipeline` MUST describe the run asked about or answer `404`. It previously
  served a six-stage fixture flow (CHECKOUT → … → DEPLOY, with tools no run had used) for any run whose
  stages were not recorded — which is every run while it is still executing. That fixture reads as the run's
  real pipeline and cannot be told apart from one.
- **FR-015**: The trigger MUST record the pipeline's declared stages and steps, every one `Pending`, when the
  run starts, so a live run has a real breakdown to show before it produces its first step result. The
  terminal record replaces it with the executed one.
- **FR-016**: The Pipeline screen MUST follow the active project scope, name the run it is describing, and
  link to it — and a run MUST link to its own pipeline. It previously showed the newest run on the server
  regardless of scope, with no way to reach the pipeline of the run being viewed.

## Out of Scope

- Per-user or per-role project visibility, and any user store beyond the single configured operator.
- A push-based registration API called by the event source, and any credential handed to the runner.
- A server-side `project` query parameter on the runs endpoint or a parameterized run stream.
- Persisting sessions across restarts.
- Editing a derived project's source or descriptor from the picker; registering a descriptor under the same
  name through the existing endpoint already covers the upgrade path (FR-009).
