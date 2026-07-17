# Feature Specification: Server / Read API

**Feature Branch**: `007-server-api`

**Created**: 2026-07-15

**Status**: Draft

**Input**: User description: "A long-running HTTP service exposing Kontinuance's run history (the 006 RunStore) over a REST API so the future Web UI and operators can list recent runs and inspect a single run. First increment Spring-free on the JDK HttpServer, zero new dependencies (Spring Boot adoption a deliberate later step). Endpoints: GET /api/health; GET /api/runs?limit=N (newest-first, bounded); GET /api/runs/{id} (404 when absent). JSON from the persisted RunRecord. An installable kontinuance-api launcher serves the store under ~/.kontinuance/runs on a configurable host/port. HTTP behind handlers so a Spring/SSE transport can wrap the same read logic later. Out of scope: SSE/WebSocket streaming, manual-trigger POST, auth, real Spring Boot."

## User Scenarios & Testing *(mandatory)*

Kontinuance now records every run to a durable store (006), but that history can only be read by inspecting files on disk — there is no way for a program (or a person over the network) to query it. This feature stands up a small long-running HTTP service that serves the run history over a stable read API, so the forthcoming Web UI — and operators with a browser or `curl` — can list recent runs and open one. It is the backbone the UI's run-list and run-detail views read from. Users: the Web UI (primary consumer) and operators querying directly.

### User Story 1 - List recent runs over HTTP (Priority: P1)

A consumer (the UI, or an operator) asks the service for the most recent runs and gets them back newest-first, each with enough to populate a list row: id, pipeline, status, timing, and CI context.

**Why this priority**: The run list is the entry point of any run-history view — without it there is nothing to show. It is the irreducible core of the API.

**Independent Test**: With a store holding several runs, request the run list with a limit and assert the response contains at most that many runs, newest-first, each carrying id/pipeline/status.

**Acceptance Scenarios**:

1. **Given** a store with N recorded runs, **When** the run list is requested with limit L, **Then** the response contains at most L runs, newest-first, each with id, pipeline, status, timing, and (when present) repo/sha/trigger.
2. **Given** an empty store, **When** the run list is requested, **Then** the response is an empty list with a success indication (not an error).
3. **Given** a request with no/invalid limit, **When** it is handled, **Then** a sensible default bound is applied rather than returning an unbounded or failing response.

### User Story 2 - Inspect a single run (Priority: P1)

A consumer opens one run by id and gets its full recorded detail; asking for an unknown id gets a clear "not found".

**Why this priority**: The run-detail view is the other half of the minimum useful API; list + detail together are the MVP.

**Independent Test**: Fetch a known run id and assert its fields match the record; fetch an unknown id and assert a not-found response.

**Acceptance Scenarios**:

1. **Given** a recorded run id, **When** it is requested, **Then** the response returns that run's full recorded detail.
2. **Given** an id with no record, **When** it is requested, **Then** the response indicates "not found" distinctly from a server error.

### User Story 3 - Run the service against real recorded history (Priority: P2)

An operator starts the service, pointed at the location where the CI service records runs, on a chosen address, and it serves that live history; a health check confirms it is up.

**Why this priority**: The API is only useful running as a service over the real store; it builds on US1–US2 but is a distinct operational capability.

**Independent Test**: Start the service over a store directory that the CI service writes to, hit the health endpoint and the run list, and confirm it returns the recorded runs; confirm the bind address is configurable.

**Acceptance Scenarios**:

1. **Given** the service started over the CI store location, **When** the health endpoint is requested, **Then** it reports healthy.
2. **Given** the CI service has recorded runs, **When** the run list is requested from the started service, **Then** those runs are returned.
3. **Given** a configured host/port, **When** the service starts, **Then** it binds there (and fails clearly if it cannot).

### Edge Cases

- Unknown path or method: returns a clear not-found / method-not-allowed rather than a stack trace.
- Malformed record on disk: excluded from results without failing the whole request (reuses the store's corrupt-record isolation).
- Very large history: the list is always bounded by the effective limit; there is a maximum cap even if a larger limit is requested.
- Store location missing/empty at startup: the service still starts and serves an empty list (history appears as runs are recorded).
- Concurrent requests: reads are safe to serve simultaneously.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose an HTTP endpoint that returns the most recent runs newest-first, each including at least id, pipeline, status (and failing step when failed), start/end time, and CI context (repo, sha, trigger) when present.
- **FR-002**: The run-list endpoint MUST accept a caller-supplied limit, apply a sensible default when absent/invalid, and enforce a maximum cap so a response is never unbounded.
- **FR-003**: The system MUST expose an HTTP endpoint that returns a single run's full recorded detail by id, and MUST return a distinct "not found" response for an unknown id.
- **FR-004**: The system MUST expose a health endpoint that reports the service is up.
- **FR-005**: Responses MUST be a machine-readable structured format (JSON) derived from the persisted run records; no secret values appear (records already carry none).
- **FR-006**: Unknown routes/methods MUST return a clear client-error response (not a server error or stack trace).
- **FR-007**: The service MUST run as a long-running process bound to a configurable host and port, reading the run history from a configurable store location (defaulting to where the CI service records runs), and MUST fail clearly if it cannot bind.
- **FR-008**: The read logic MUST sit behind transport-agnostic handlers so a different HTTP transport (e.g. a Spring Boot / streaming layer) can serve the same behavior later without changing the read logic.
- **FR-009**: This feature MUST NOT change the pipeline engine's public contract and MUST NOT add a new external dependency in this increment (the HTTP surface uses the platform's built-in capabilities); Spring Boot adoption, if pursued, is a separate deliberate change.
- **FR-010**: The API's response shape and routes are a consumer-facing contract (the UI depends on them) and MUST be stable/versioned under a clear path prefix (Constitution I).

### Key Entities

- **Run summary / detail resource**: the API's view of a persisted run record (id, pipeline, status, timing, CI context), serialized as JSON.
- **Run-history API**: the set of read routes (health, list, detail) under a stable path prefix.
- **Read handler**: the transport-agnostic unit that turns a request (list/detail/health) into a response from the store, independent of the HTTP server used.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A consumer can retrieve the most recent runs newest-first and open any one by id using only the API, with no file access.
- **SC-002**: The run list never returns more than the effective limit, and applies a default when none is given (0 unbounded responses).
- **SC-003**: An unknown run id returns a distinct not-found result in 100% of cases (never a server error).
- **SC-004**: The running service serves the exact runs the CI service recorded (0 discrepancy between recorded and served history).
- **SC-005**: The service binds to the configured host/port, and reports a clear failure if the address is unavailable.
- **SC-006**: 0 new external dependencies are added and the engine build stays green with its gates.
- **SC-007**: The read handlers are exercised independently of the HTTP server (transport-agnostic), demonstrating the seam for a future streaming transport.

## Assumptions

- Homelab scale and a trusted network for this increment: no authentication/authorization yet (a follow-up), so the service is intended to run on a private network.
- JSON over HTTP GET is the appropriate contract for the Web UI's read views; live streaming (SSE/WebSocket) and write actions (manual trigger) are explicitly deferred to follow-ups.
- The store is the 006 file-backed `RunStore`; the API reads through the same interface, so a future database backend serves the same API unchanged.
- The built-in HTTP capabilities of the platform are sufficient for a read API at this scale; a full application framework (Spring Boot) is a later, deliberate adoption gated on its dependency/verification work.
- The Web UI design (maintainer's screenshots) will refine the exact resource fields and any additional read routes; this increment provides the run list + detail + health that every run-history view needs.
