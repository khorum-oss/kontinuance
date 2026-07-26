# Feature Specification: Editable Config Screen (edit/validate/save kontinuance.yml)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User pivoted to making the Config screen **editable**. Today it is read-only — an operator edits
the `kontinuance.yml` the server points at by hand and restarts. This lets them view, edit, validate, and
save the pipeline descriptor from the web UI, with the server's own strict parser guarding every save so a
broken descriptor can never be written.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Edit and save the descriptor from the UI (Priority: P1)

An operator opens the Config screen, edits the descriptor source, and saves it. On success the file the
server runs is updated and the screen reflects the new source and resolved plan — no manual file editing or
restart.

**Why this priority**: The pipeline definition is the one thing operators change most; hand-editing the file
on the server is the last manual step in the loop.

**Independent Test**: Open Config, enter edit mode, change the source, save, and see the new source and plan
rendered back — and the descriptor file on disk holds the new text.

**Acceptance Scenarios**:

1. **Given** the Config screen, **When** the operator edits the source and saves a valid descriptor, **Then**
   it is persisted to the descriptor file and the screen shows the refreshed source and plan.
2. **Given** a saved edit, **When** the pipeline is next triggered, **Then** it runs the newly-saved
   descriptor (the same file the server already reads and runs).

---

### User Story 2 - A bad edit is rejected, never written (Priority: P1)

If the edited descriptor doesn't parse, the save is rejected with the parser's message shown inline, the
editor stays open with the operator's text, and the file on disk is left untouched.

**Why this priority**: Saving a broken descriptor would break the next run; validation must gate the write so
a good file is never clobbered by a typo.

**Independent Test**: Enter an invalid descriptor, save, and see the validation error inline; the editor
stays open and the on-disk file is unchanged.

**Acceptance Scenarios**:

1. **Given** an invalid edit, **When** the operator saves, **Then** the save is rejected with the parser's
   location-tagged message, the editor stays open, and the descriptor file is unchanged.

### Edge Cases

- **Malformed request** (not the expected shape): rejected as a bad request; nothing is written.
- **No descriptor yet**: saving a valid descriptor creates the file (parent directories included).
- **Auth**: when auth is enforced, saving requires a session like every other non-public endpoint.
- **Contract preserved**: the save returns the same `/api/config` shape the screen already reads, so only the
  ability to write is added.

## Requirements *(mandatory)*

- **FR-001**: The Config screen MUST offer an edit mode over the descriptor source, with save and cancel.
- **FR-002**: Saving MUST validate the edited descriptor with the engine's strict parser and persist it to
  the descriptor file **only if it parses**; a save MUST return the refreshed config projection.
- **FR-003**: An invalid edit MUST be rejected with the parser's message (shown inline), MUST NOT overwrite
  the on-disk file, and MUST keep the editor open with the operator's text.
- **FR-004**: The saved file MUST be the same descriptor the server reads and runs, so the change takes
  effect on the next trigger without a restart.
- **FR-005**: The change MUST NOT alter the `/api/config` response shape (only add a write); no new
  dependency; the write endpoint is gated by auth like other non-public endpoints.

## Success Criteria *(mandatory)*

- **SC-001**: A valid edit saved from the UI persists to the descriptor file and refreshes the screen.
- **SC-002**: An invalid edit is rejected with the parser's message, the editor stays open, and the file is
  unchanged.
- **SC-003**: The saved descriptor is what the next triggered run uses.
- **SC-004**: No `/api/config` shape change; no new dependency; suites stay green.

## Assumptions

- **The engine parser is the single source of validity.** A save is accepted iff `PipelineDescriptor.parse`
  accepts it, so the on-disk file is always a runnable descriptor — the same guarantee a hand-edit would
  need, enforced automatically.
- **One descriptor file.** The server points at a single configured descriptor (`kontinuance.config
  .descriptor`); editing targets that file. Per-project/multi-descriptor management is a later feature.
