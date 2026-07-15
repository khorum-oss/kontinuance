# Feature Specification: Run History Persistence

**Feature Branch**: `006-run-persistence`

**Created**: 2026-07-15

**Status**: Draft

**Input**: User description: "Persist pipeline run history so it survives process restarts and can be listed/queried later — the durable store the future API and UI read from. Record each run (id, pipeline, status, timing, and CI context: repo/sha/trigger) behind a swappable RunStore interface with a durable default and an in-memory test double, and fold in the 003 poll cursor so state lives in one place. Wire recording into the github CI service. Keep engine-only and Spring-free; no new external dependency (JSON via the catalog's serialization-json)."

## User Scenarios & Testing *(mandatory)*

Today a Kontinuance run's outcome is reported to GitHub and streamed to stdout, then forgotten — the engine is stateless and the CI service keeps only a poll cursor. There is no way to answer "what ran, when, and how did it end?" after the fact. This feature adds a durable **run history**: every run is recorded to a store that survives restarts and can be listed and fetched by id. It is the backing store the future Server/API and Web UI read from, and it consolidates the 003 poll cursor into the same durable location. Users: operators inspecting history now (by reading the store), and — soon — the API/UI.

### User Story 1 - Runs are recorded and survive a restart (Priority: P1)

When the CI service runs a pipeline for a repository event, the run is recorded — its id, pipeline, final status, start/end time, and the CI context (repo, head SHA, trigger kind). After the service restarts, that history is still there.

**Why this priority**: A durable record of runs is the irreducible core — without it there is no history for anything (operators, API, UI) to read. Everything else builds on it.

**Independent Test**: Run a pipeline through the CI service against a stand-in GitHub, then construct a fresh store over the same location and confirm the run is present with its status and context.

**Acceptance Scenarios**:

1. **Given** a CI run that completes, **When** it finishes, **Then** a record with its id, pipeline, final status, timing, and repo/SHA/trigger is written to the store.
2. **Given** recorded runs and a restart, **When** a new store is opened over the same location, **Then** the previously recorded runs are readable.
3. **Given** a run that fails, **When** it is recorded, **Then** the record's status reflects the failure (and, where applicable, the failing step).

### User Story 2 - History is listable and fetchable (Priority: P2)

An operator (or the future API/UI) can list the most recent runs, newest first, and fetch a single run by id.

**Why this priority**: Recording is only useful if it can be read back in a predictable order. It builds on US1 but is a distinct capability (query vs. write).

**Independent Test**: Record several runs, then list with a limit and assert newest-first ordering and the limit; fetch one by id and assert it matches.

**Acceptance Scenarios**:

1. **Given** N recorded runs, **When** the most recent are listed with a limit L, **Then** at most L runs are returned, newest first.
2. **Given** a known run id, **When** it is fetched, **Then** the matching record is returned; an unknown id returns nothing.

### User Story 3 - One durable place for state; pluggable backend (Priority: P3)

The run history and the 003 poll cursor live behind small interfaces with a durable default, so a future backend (e.g. a database in the Server/API feature) can replace the file default without changing callers, and the CI service's cursor no longer needs its own separate file.

**Why this priority**: Consolidation and a swappable seam matter for the platform's evolution but are not required to prove history works; they harden US1–US2.

**Independent Test**: Swap the in-memory store for the durable one (and vice-versa) behind the interface with no caller change; confirm the CI service persists its cursor through the same store location.

**Acceptance Scenarios**:

1. **Given** the store interface, **When** the in-memory and durable implementations are exchanged, **Then** callers compile and behave the same (aside from durability).
2. **Given** the CI service, **When** it records a cursor and restarts, **Then** it resumes from the persisted cursor.

### Edge Cases

- A corrupt or partially written record on disk: reading skips/isolates the bad entry rather than failing the whole history load.
- Concurrent runs recording at once: each record is written without corrupting others.
- A very large history: listing is bounded by the requested limit (no unbounded load).
- No store configured: recording is a no-op sink (the engine/CI path still works) rather than an error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST record each completed run to a store as a record carrying at least: run id, pipeline name, final status (and failing step when failed), start and end time, and — for CI runs — repo, head SHA, and trigger kind.
- **FR-002**: The store MUST be durable by default: records written before a restart MUST be readable after it.
- **FR-003**: The system MUST list recorded runs newest-first, bounded by a caller-supplied limit, and MUST fetch a single run by id (absent id → no result).
- **FR-004**: Recording and querying MUST sit behind a small interface with at least a durable implementation and an in-memory implementation, exchangeable without changing callers.
- **FR-005**: The github CI service MUST record every run it executes (pending→terminal) via the store.
- **FR-006**: The 003 poll cursor MUST be consolidatable into the same durable location so run state lives in one place (the CI service resumes its cursor across restarts).
- **FR-007**: A malformed on-disk record MUST NOT prevent loading the rest of the history.
- **FR-008**: Persistence MUST NOT require a new external dependency, MUST stay engine-only and Spring-free, and MUST NOT change the pipeline engine's public contract.
- **FR-009**: Secret values MUST NOT appear in any persisted record (reuses masking; records store status/context, not step logs or secret values).

### Key Entities

- **RunRecord**: the persisted summary of one run — id, pipeline, status (+ failing step), start/end, and optional CI context (repo, sha, trigger).
- **RunStore**: the interface to record, list (newest-first, bounded), and fetch runs; durable + in-memory implementations.
- **Poll cursor state**: the last head SHA per tracking key, consolidated into the durable store location.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of runs executed by the CI service produce a readable record with correct status and CI context.
- **SC-002**: After a restart, previously recorded runs are still readable (0 lost across a clean restart).
- **SC-003**: Listing returns runs newest-first and never more than the requested limit.
- **SC-004**: Swapping the durable and in-memory implementations requires 0 caller changes.
- **SC-005**: A deliberately corrupted record leaves the remaining history readable (history load never fails wholesale).
- **SC-006**: 0 new external dependencies added; the engine build stays Spring-free and its gates green.
- **SC-007**: 0 secret values appear in any persisted record.

## Assumptions

- Homelab scale (a handful of repos, low run volume), so a file-backed durable store is sufficient for this feature; a database backend can replace it behind the interface in the Server/API feature.
- JSON via the catalog's `kotlinx-serialization-json` runtime (no compiler plugin, no new dependency) is an acceptable on-disk format.
- Records store run *metadata/status*, not step log streams (logs remain a stdout/streaming concern), so no secret values are persisted.
- The engine remains stateless; recording is done by the caller (the CI service now; the engine CLI and Server/API later), not inside the engine core.
