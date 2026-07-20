# Feature Specification: Server Authentication & Session

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-20

**Status**: Draft

**Input**: User description: "Add real authentication to the Kontinuance server so its write and read API is no longer open. Configured operator credentials; when set, auth is enforced; when unset, the server stays open but warns. A sign-in establishes an HttpOnly session cookie. Provide login, who-am-I, and logout. Public paths: the auth endpoints, health, and actuator health. No new dependency; verification metadata stays enabled. Web UI wiring is a follow-up."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enforce authentication on the API when credentials are configured (Priority: P1)

An operator who has configured a username and password expects that anyone reaching the API must sign in
first. Unauthenticated calls to the run, trigger, approve/reject, and live-stream endpoints are rejected;
only after a successful sign-in do they succeed.

**Why this priority**: This is the headline capability — the API is currently wide open, and the docs flag
it as the top security limitation. Without enforcement, nothing else in the feature matters.

**Independent Test**: With credentials configured, call a protected endpoint with no session and observe a
rejection (unauthorized); sign in; repeat the call carrying the session and observe success.

**Acceptance Scenarios**:

1. **Given** credentials are configured, **When** a protected API endpoint is called with no valid session,
   **Then** the request is rejected as unauthorized (and no run is triggered/approved).
2. **Given** credentials are configured, **When** an operator signs in with the correct username and
   password, **Then** a session is established and subsequent calls carrying that session succeed.
3. **Given** credentials are configured, **When** an operator signs in with a wrong username or password,
   **Then** sign-in is rejected as unauthorized and no session is established.

---

### User Story 2 - Stay open (with a warning) when no credentials are configured (Priority: P1)

An operator running locally on loopback, or the existing automated tests, must keep working without any
credential setup — but the operator is clearly warned that the API is unprotected.

**Why this priority**: The default loopback deployment and the current integration-test suite depend on the
API being reachable without a login. Enforcement must be opt-in via configuration so nothing that works
today silently breaks; the warning keeps the open mode from being an invisible foot-gun.

**Independent Test**: With no credentials configured, call a protected endpoint with no session and observe
success, and confirm a warning about the unprotected API is emitted at startup.

**Acceptance Scenarios**:

1. **Given** no credentials are configured, **When** any API endpoint is called with no session, **Then**
   the request succeeds (open mode).
2. **Given** no credentials are configured, **When** the server starts, **Then** a clear warning states the
   API is running without authentication.

---

### User Story 3 - Sign in, check identity, and sign out (Priority: P2)

An operator can sign in, ask the server who they are signed in as (and whether authentication is even
required in this deployment), and sign out to end the session.

**Why this priority**: These operations are what a UI (a later feature) and a human operator need to drive
the session — but the enforcement in US1/US2 is the substance; identity/logout ergonomics sit on top.

**Independent Test**: Sign in; query the who-am-I operation and see the signed-in identity plus that auth is
required; sign out; query again and see no identity / an unauthorized result.

**Acceptance Scenarios**:

1. **Given** an established session, **When** the who-am-I operation is called, **Then** it returns the
   signed-in username and that authentication is required.
2. **Given** an established session, **When** the operator signs out, **Then** the session is invalidated and
   subsequent protected calls are rejected as unauthorized.
3. **Given** no credentials are configured, **When** the who-am-I operation is called, **Then** it reports
   that authentication is **not** required (so a client knows not to gate its UI).

### Edge Cases

- **Health and actuator health stay reachable without a session** — a load balancer / uptime check must
  work whether or not auth is enforced; the auth endpoints themselves are likewise always reachable (else
  no one could sign in).
- **A stale or forged session token** (unknown, tampered, or already-signed-out) is treated as no session —
  the request is rejected when auth is enforced.
- **The live-stream (SSE) and websocket endpoints** are protected the same as the rest of the API when auth
  is enforced — an unauthenticated stream connection is rejected.
- **Wrong-username vs wrong-password** are indistinguishable to the caller (both a generic unauthorized) and
  take effectively the same time, so the endpoint does not leak which field was wrong.
- **Only a username or only a password configured (not both)** is treated as "not configured" (open mode +
  warning), not as a half-enabled state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support operator-configured credentials — a single username and password —
  supplied through configuration/environment, never hard-coded and never committed. Authentication is
  **enforced** when (and only when) both are configured.
- **FR-002**: When authentication is enforced, the system MUST reject any request to a non-public endpoint
  that does not carry a valid session, without performing the requested action, returning an unauthorized
  result.
- **FR-003**: When credentials are **not** configured, the system MUST allow all requests (open mode) and
  MUST emit a clear startup warning that the API is unauthenticated.
- **FR-004**: The system MUST provide a **sign-in** operation that accepts a username and password, and on a
  correct match establishes a session and returns it to the client via an **HttpOnly** session cookie; on a
  mismatch it MUST reject the attempt as unauthorized and establish no session.
- **FR-005**: The system MUST provide a **who-am-I** operation that reports, for the current request, whether
  authentication is required in this deployment and — when a valid session is present — the signed-in
  username.
- **FR-006**: The system MUST provide a **sign-out** operation that invalidates the current session so that
  subsequent protected requests carrying the old session are rejected.
- **FR-007**: The following paths MUST be public (reachable without a session) in all modes: the
  authentication operations (sign-in / who-am-I / sign-out), the API health check, and actuator health.
  Every other API path — including the live run stream and the websocket — MUST require a valid session when
  authentication is enforced.
- **FR-008**: Credential comparison MUST be done in **constant time** (independent of how many leading
  characters match), so response timing does not reveal the configured secret.
- **FR-009**: The session cookie MUST be marked HttpOnly (not readable by page scripts) and scoped so the
  browser returns it on same-origin API calls.
- **FR-010**: The change MUST introduce **no new third-party dependency**; dependency verification MUST
  remain enabled and its metadata unchanged (Constitution Principle V).

### Key Entities *(include if feature involves data)*

- **Operator credentials**: the single configured username + password that gate the API; presence of both
  toggles enforcement. Provided by the operator, held only in server configuration/memory.
- **Session**: a server-issued opaque token bound to the signed-in username, carried by the client as an
  HttpOnly cookie, created at sign-in and destroyed at sign-out; valid for the life of the running server
  (no cross-restart persistence in this feature).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With credentials configured, 100% of calls to protected endpoints without a valid session are
  rejected, and the underlying action (trigger/approve/reject) does not occur.
- **SC-002**: With credentials configured, an operator who signs in correctly can then reach every protected
  endpoint they could reach before the feature existed.
- **SC-003**: With no credentials configured, every endpoint that worked before the feature still works with
  no sign-in, and the existing automated test suite stays green.
- **SC-004**: A wrong username and a wrong password each yield the same generic unauthorized result, and the
  who-am-I operation reflects the session state (identity when signed in; "auth not required" in open mode).
- **SC-005**: After sign-out, a protected call carrying the invalidated session is rejected.
- **SC-006**: The dependency set and verification metadata are unchanged by the feature (no new dependency).

## Assumptions

- **Single operator, single credential pair.** Multi-user accounts, roles, password hashing/rotation, and
  external SSO/OAuth are explicitly out of scope; this is the minimal real gate the docs call for. A future
  feature can layer richer identity on the same session seam.
- **Sessions are in-memory and single-instance.** They do not survive a server restart and are not shared
  across instances — consistent with the existing single-instance durability model (only paused runs survive
  a restart). Persisted/clustered sessions are a later concern.
- **The web UI wiring is a separate follow-up**: calling sign-in from the login screen, showing the
  signed-in operator's name in the sidebar (instead of the current placeholder), and making EXIT return to
  the project view rather than the sign-in screen. This feature delivers the server capability those will
  consume.
- **Same-origin deployment.** The UI and API are served on one origin (per the running guide), so a
  same-site session cookie is sufficient; cross-origin token schemes are not needed.
- **Transport security is the operator's responsibility** (TLS terminated at the reverse proxy), unchanged
  from today; this feature adds the authentication gate, not transport encryption.
