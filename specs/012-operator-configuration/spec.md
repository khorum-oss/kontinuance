# Feature Specification: Operator Configuration & Deployment Guide

**Feature Branch**: `claude/operator-configuration`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "an operator configuration & deployment guide for running Kontinuance in a real environment, aligned with the existing specs (001–011). Documentation and example configuration artifacts only — no engine/server/web code changes."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Stand up the service and web UI on one origin (Priority: P1)

An operator has the built server and the built web app and wants to run them together in their own
environment. Using the guide, they set the store location, descriptor path, coverage report path, and
bind address/port, start the server, and place the static web app and the API behind one reverse proxy
so the UI and its `/api` + `/ws` calls share a single origin.

**Why this priority**: Without a documented configuration surface and a same-origin serving model, the
UI cannot reach the API and the operator cannot run the product at all. This is the minimum viable
outcome of the whole feature.

**Independent Test**: Following only the guide and the reverse-proxy example, an operator can start the
server, serve the web app, load it in a browser, and see the runs list populate from the live API —
without reading source code.

**Acceptance Scenarios**:

1. **Given** the guide and a fresh environment, **When** the operator sets each documented configuration
   value, **Then** every value the server and web app read at runtime is covered (store, descriptor,
   coverage report, bind address/port, stream tuning, secrets, web API target) with its name and default.
2. **Given** the reverse-proxy example, **When** the operator applies it, **Then** the static web app is
   served and `/api` and `/ws` are proxied to the server on the same origin, including the WebSocket
   upgrade for `/ws`.

---

### User Story 2 - Author a valid gated pipeline from an example (Priority: P2)

An operator wants to define their own pipeline with a manual approval before deployment. They copy the
example descriptor as a starting point and adapt it, confident it follows the parser's rules and the
gate-in-its-own-stage guidance so a resumed run repeats no prior work.

**Why this priority**: A correct, copyable example prevents the most common authoring mistakes (unknown
keys, multiple step types, a gate sharing a stage with other work) and makes the approval feature usable
in practice.

**Independent Test**: The example descriptor can be loaded by the running server as its configured
descriptor and accepted (a run can be triggered from it) without edits.

**Acceptance Scenarios**:

1. **Given** the example descriptor, **When** it is used as the server's configured descriptor, **Then**
   it is accepted by the strict parser (valid top-level shape, one step type per step, `when:` for
   conditions).
2. **Given** the example descriptor, **When** an operator inspects it, **Then** the manual-approval gate
   is in its own stage, positioned between the build/test stages and the deploy stage.

---

### User Story 3 - Understand the security and durability limits before exposing the service (Priority: P3)

Before putting Kontinuance on a network, an operator reads the guide's limitations so they make informed
choices: that the endpoints are unauthenticated, that only gate-paused runs survive a restart, and that
the durable gate assumes a single instance.

**Why this priority**: These constraints are safety-critical for real deployments but do not block a
local/loopback bring-up, so they rank below getting the service running.

**Independent Test**: The guide states each limitation explicitly with the operator action it implies,
and can be checked by reading the "limitations" section against this spec's requirements.

**Acceptance Scenarios**:

1. **Given** the guide, **When** an operator reads the limitations, **Then** the no-authentication
   posture, the loopback-by-default bind, and the "put an authenticating proxy in front before exposing"
   guidance are all stated.
2. **Given** the guide, **When** an operator reads the durability section, **Then** it states that only
   gate-paused runs survive a restart, that actively-running runs do not, and that the durable gate
   assumes a single instance sharing the store.

### Edge Cases

- What happens when the configured descriptor path does not exist or is invalid? The guide explains the
  operator-visible result (a trigger is rejected; the config screen falls back to fixture data) so the
  operator can diagnose it.
- What happens when the coverage report path is absent? The guide explains the fallback behavior so a
  missing report is not mistaken for a broken screen.
- What happens when the operator exposes the port without a proxy? The guide's security section calls out
  the risk explicitly.
- What happens when the web app is served on a different origin than the API? The guide states that the
  same-origin model is required and the proxy example is the supported path.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The guide MUST document every runtime configuration value the server and web app read, each
  with its name (property and/or environment variable), meaning, and default: run-history store location,
  pipeline descriptor path, coverage report path, server bind address and port, live-stream poll interval
  and snapshot limit, pipeline secrets source, and the web app's API target.
- **FR-002**: The guide MUST document how pipeline secrets are supplied (as environment variables read by
  the engine) and MUST NOT contain any real secret values — only placeholder names.
- **FR-003**: The guide MUST describe the same-origin serving model: the web app is a static single-page
  app that must be served with `/api` and `/ws` reaching the server on the same origin.
- **FR-004**: A reverse-proxy configuration example MUST be provided that serves the built static web app
  and proxies `/api` and `/ws` to the server, including the WebSocket upgrade for `/ws`.
- **FR-005**: An example pipeline descriptor MUST be provided that is valid against the current strict
  parser and demonstrates build and test stages, a manual-approval gate in its own stage, and a deploy
  stage, in that order.
- **FR-006**: The guide MUST state the authentication posture: the trigger, approve, and reject endpoints
  are unauthenticated in this release; the default loopback bind is the safe default; an authenticating
  proxy must front the service before it is exposed on a network.
- **FR-007**: The guide MUST state the durability boundary: runs paused at an approval gate survive a
  restart (resolved from the persisted store), while actively-executing runs do not, and the durable gate
  assumes a single server instance sharing one store.
- **FR-008**: The guide MUST state the descriptor authoring rules an operator needs: the top-level shape,
  `when:` for step conditions, exactly one step type per step, and placing the approval gate in its own
  stage so a resumed run repeats no prior work.
- **FR-009**: The guide MUST describe the operator-visible fallback behavior when the descriptor or the
  coverage report is missing or invalid, so those conditions are diagnosable rather than mysterious.
- **FR-010**: All artifacts MUST reference only in-repository behavior and MUST NOT link to any external
  design source.
- **FR-011**: This feature MUST NOT change any engine, server, or web application code — it adds
  documentation and example configuration files only.

### Key Entities *(include if feature involves data)*

- **Run/config guide**: the operator-facing document that consolidates the configuration surface, the
  serving model, and the operational limitations.
- **Example descriptor**: a copyable, parser-valid pipeline definition demonstrating a gated flow.
- **Reverse-proxy example**: a copyable proxy configuration that serves the SPA and proxies the API and
  WebSocket on one origin.
- **Configuration value**: a named setting (property and/or environment variable) with a default and a
  documented effect on runtime behavior.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Using only these artifacts, an operator can set every runtime configuration value and start
  the server and web app behind a reverse proxy on one origin, with no need to read source code.
- **SC-002**: 100% of the runtime configuration values the server and web app actually read are documented
  with name and default (no undocumented setting an operator must discover from code).
- **SC-003**: The example descriptor is accepted by the running server as its configured descriptor with
  no edits (a run can be triggered from it).
- **SC-004**: An operator can, from the guide alone, correctly state the three key limitations
  (no authentication, only gate-paused runs are restart-durable, single-instance durable gate) before
  exposing the service.
- **SC-005**: No real secret value appears in any artifact; every credential is a placeholder name.

## Assumptions

- The reader has already built the server and the web app; building/packaging steps are only referenced,
  not re-documented here.
- A single reverse-proxy example (nginx and/or Caddy) is sufficient; other proxies are analogous and out
  of scope.
- The system is operated as a single instance sharing one run store (consistent with the current durable
  approval design); multi-instance operation is out of scope.
- Authentication is out of scope for this release and is documented as a known limitation rather than
  implemented here.
- Container images and orchestration manifests (Kubernetes/Compose) are out of scope beyond the
  reverse-proxy example.
- The configuration names, defaults, and behaviors documented here match the current server and web app
  (features 007–011); this feature does not change them.
