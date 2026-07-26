# Feature Specification: Project Registry & Real Entry-Screen Project Picker

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-26

**Status**: Draft

**Input**: User: "First-run repo/descriptor setup" → "Real projects on the entry screen." The entry shell's
second step was a cosmetic repo picker (hard-coded repos, provider filters, a fake "add repo" that only
touched local state). This makes it real: the server stores **named pipeline descriptors ("projects")**,
the entry screen lists them, you add one (name + descriptor, validated by the engine parser), and selecting
a project **activates** it — the server points its live descriptor at that project, so the trigger and Config
screen run it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pick a project to run (Priority: P1)

After signing in (or straight away in open mode), an operator sees the projects the server knows about, with
the active one marked, and clicks one to make it active and enter mission control.

**Why this priority**: The entry screen was the last fake-data surface — a picker that selected nothing real.
Selecting a project must actually decide what the engine runs.

**Independent Test**: Sign in, see the seeded projects, click one, and land in mission control with that
project active.

**Acceptance Scenarios**:

1. **Given** a server with at least one project, **When** the operator opens the entry screen, **Then** the
   projects are listed and the active one is badged `ACTIVE`.
2. **Given** the project list, **When** the operator clicks a project, **Then** the server makes it active
   (points its live descriptor at it) and the operator enters mission control.

---

### User Story 2 - Add a project (Priority: P1)

An operator adds a new project by giving it a name and pasting a pipeline descriptor; the server validates
the descriptor with the engine's strict parser and only stores it if it parses.

**Why this priority**: A registry with no way to add to it isn't a registry. Validation on add keeps a broken
descriptor from ever becoming selectable.

**Independent Test**: Add a project with a valid descriptor and see it in the list; add one with an invalid
descriptor and see it rejected inline, not stored.

**Acceptance Scenarios**:

1. **Given** the add panel, **When** the operator submits a valid name + descriptor, **Then** the project is
   stored and appears in the list.
2. **Given** the add panel, **When** the descriptor does not parse, **Then** the server rejects it with the
   parser's message shown inline and the project is not stored.
3. **Given** a name that already exists, **When** the operator submits it, **Then** it is rejected as a
   duplicate.

---

### User Story 3 - First-run seeding (Priority: P2)

On a fresh server that already has a descriptor on disk but no projects registered, the first look at the
entry screen registers that descriptor as a `default` project and marks it active, so there is always
something to select.

**Why this priority**: A brand-new install should not present an empty picker when a working descriptor
already exists.

**Independent Test**: With a descriptor file present and no projects, open the entry screen and see a
`default` project, active.

**Acceptance Scenarios**:

1. **Given** a descriptor file and no registered projects, **When** the projects are listed, **Then** a
   `default` project is seeded from that descriptor and marked active.

### Edge Cases

- **Editing then switching**: editing the descriptor on the Config screen (027) updates the **active**
  project's stored snapshot, so switching away and back preserves the edit rather than reverting.
- **Unsafe names**: a project name must be a safe slug (letters, digits, `. _ -`, 1–64 chars) so it can never
  escape the store directory; anything else is rejected `400`.
- **Unknown activate**: activating a name that is not a project is `404`.
- **No descriptor to seed**: if there is no descriptor file (or it does not parse), nothing is seeded and the
  picker shows an honest "no projects yet — add one" state.

## Requirements *(mandatory)*

- **FR-001**: The server MUST store named pipeline descriptors ("projects") and expose `GET /api/projects`
  returning the projects and which one is active.
- **FR-002**: `POST /api/projects` MUST register `{name, text}` after validating the name is a safe slug
  (else `400`), the name is not already taken (else `409`), and the text parses with the engine's strict
  parser (else `400` with the parser's message); it MUST never store an invalid descriptor.
- **FR-003**: `POST /api/projects/{name}/activate` MUST make a project active by writing its descriptor to the
  server's live descriptor file (so the trigger and Config screen use it) and recording it as active; an
  unknown project is `404`.
- **FR-004**: On first use (no projects registered) the server MUST seed a `default` project from the current
  descriptor file when one exists and parses, and mark it active.
- **FR-005**: Editing the descriptor via `PUT /api/config` MUST keep the active project's stored snapshot in
  sync with the edit.
- **FR-006**: The entry screen MUST list the projects (active one marked), let the operator add one (name +
  descriptor, with server-side validation surfaced inline), and activate one by clicking it before entering
  mission control.
- **FR-007**: The change MUST introduce no new dependency; descriptor validation reuses the engine parser and
  the store is file-backed under the existing run-store base directory.

## Success Criteria *(mandatory)*

- **SC-001**: An operator can see the server's projects, add a new one, and select one to run — end to end.
- **SC-002**: An invalid descriptor (or bad/duplicate name) is rejected and never stored, with the reason
  shown inline.
- **SC-003**: Selecting a project changes what the trigger and Config screen run (the live descriptor points
  at it).
- **SC-004**: A fresh server with an existing descriptor shows a seeded `default` project rather than an empty
  picker.
- **SC-005**: No new dependency; existing suites stay green.

## Assumptions

- **File-backed, swappable.** Each project is one descriptor's text at `<store>/projects/<name>.yml`; the
  active name lives in `<store>/projects/.active`. This mirrors the run/log stores and can be replaced by a
  database backend behind the same surface.
- **Activation copies text into the live descriptor.** Rather than threading a "current project" through the
  trigger and approvals, activating a project writes its text to the existing descriptor file the rest of the
  server already reads — the lowest-coupling way to make selection real.
- **Entry-screen scope.** This is the first-run/setup surface; per-project run history, permissions, and
  remote descriptor sources are out of scope here.
