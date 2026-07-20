# Feature Specification: Web Sign-In & Session Wiring

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-20

**Status**: Draft

**Input**: User description: "Wire the web UI to the real server authentication (016): the login screen calls the server, the app is gated by the current session, the sidebar shows the signed-in operator, and EXIT returns to the project view instead of the sign-in screen."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sign in against the real server (Priority: P1)

An operator opens the UI; when the server enforces authentication, the sign-in screen submits the entered
username and password to the server, and only a correct credential lets them proceed. A wrong credential
shows an error and keeps them on the sign-in screen.

**Why this priority**: The server gate (016) exists but the UI never calls it — the sign-in screen is
decorative. This makes the UI honor the real gate, which is the whole point of the follow-up.

**Independent Test**: With the server enforcing auth, entering a wrong password shows an error and does not
advance; entering the right one advances into the app and API calls succeed.

**Acceptance Scenarios**:

1. **Given** the server enforces auth, **When** the operator submits a wrong username/password, **Then** an
   error is shown and they remain on the sign-in screen.
2. **Given** the server enforces auth, **When** the operator submits correct credentials, **Then** they are
   signed in (a session is established) and proceed to the project view.

---

### User Story 2 - Skip sign-in when the server is open (Priority: P1)

When the server is running without configured credentials (open mode), the UI does not force a login — the
operator lands directly on the project view.

**Why this priority**: The default/dev deployment is open; forcing a fake login there is the current wart.
The UI must reflect whether the server actually requires auth.

**Independent Test**: With the server open, loading the UI does not present a credential prompt; the
operator reaches the project view directly.

**Acceptance Scenarios**:

1. **Given** the server is open (no credentials configured), **When** the UI loads, **Then** no sign-in
   step is required and the operator reaches the project view.
2. **Given** a session already exists (a returning operator), **When** the UI loads, **Then** the sign-in
   step is skipped.

---

### User Story 3 - See who you are; EXIT returns to the project view (Priority: P2)

The signed-in operator's name is shown in the sidebar (not a placeholder), and **EXIT** returns to the
project/repo view rather than the sign-in screen. A distinct sign-out action ends the session and returns to
sign-in.

**Why this priority**: These are the specific UX fixes called out ("name should reflect the logged-in
person", "Exit shouldn't bring you back to the login screen, maybe back to the project view"). They polish
the flow the first two stories establish.

**Independent Test**: After signing in as a user, the sidebar shows that user; clicking EXIT shows the
project view (still signed in); signing out returns to the sign-in screen.

**Acceptance Scenarios**:

1. **Given** a signed-in operator, **When** the app shell is shown, **Then** the sidebar shows the
   signed-in username.
2. **Given** the operator is in the app, **When** they click EXIT, **Then** they return to the project/repo
   view and remain signed in (no re-authentication needed to re-enter).
3. **Given** the operator is on the project view with auth enforced, **When** they sign out, **Then** the
   session ends and they return to the sign-in screen.

### Edge Cases

- **Server unreachable at load**: the session probe fails → the UI does not hard-lock; it falls back to the
  project view (open-mode assumption) and the app's own error states surface the unreachable server.
- **Open mode**: no "signed in as" identity and no sign-out affordance are shown (there is no session); the
  sidebar shows a neutral operator label.
- **Session lost while in the app** (e.g. server restart cleared it): protected calls begin returning
  unauthorized; the operator can EXIT/sign in again. (Automatic redirect-on-401 is a later enhancement.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: On load, the UI MUST determine from the server whether authentication is required and whether a
  session already exists, and MUST gate the experience accordingly (sign-in only when required and not
  already signed in).
- **FR-002**: The sign-in action MUST submit the entered username and password to the server; on success it
  MUST establish the session and proceed, on failure it MUST show an error and not proceed.
- **FR-003**: In open mode (server not enforcing auth) the UI MUST NOT require a sign-in and MUST land the
  operator on the project view.
- **FR-004**: The app's data calls (runs list, live stream, detail, actions) MUST occur only after the
  operator has entered the app (post-session), so an enforced deployment does not fire unauthenticated calls
  behind the sign-in screen.
- **FR-005**: The sidebar MUST display the signed-in operator's username (from the session), not a
  hard-coded placeholder; in open mode it MUST show a neutral label.
- **FR-006**: **EXIT** MUST return to the project/repo view while keeping the session; it MUST NOT return to
  the sign-in screen and MUST NOT end the session.
- **FR-007**: A distinct **sign-out** action MUST end the server session and return to the sign-in screen
  (shown only when auth is enforced).
- **FR-008**: The change MUST introduce no new runtime dependency and MUST keep the UI a same-origin client
  of the server (the session cookie flows automatically).

### Key Entities *(include if feature involves data)*

- **Session view**: the UI's read of the current auth state — whether auth is required, whether the operator
  is authenticated, and the signed-in username — obtained from the server and used to gate the flow.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With the server enforcing auth, a wrong credential is rejected in the UI with a visible error
  and no entry; a correct credential enters the app and subsequent API calls succeed.
- **SC-002**: With the server open, loading the UI reaches the project view with no credential prompt.
- **SC-003**: The sidebar shows the actual signed-in username after login.
- **SC-004**: EXIT returns to the project view (not sign-in) and the operator can re-enter without signing
  in again.
- **SC-005**: Sign-out returns to the sign-in screen and a subsequent protected call is unauthorized until
  the operator signs in again.

## Assumptions

- **Builds on 016**: the server exposes `login` / `who-am-I` / `logout` and an opt-in gate; this feature only
  wires the existing browser UI to them. The `KSESSION` cookie is HttpOnly and same-origin, so the UI never
  reads the token — it relies on the browser sending the cookie.
- **The "project view" is the existing repo-setup step** of the entry screen. Making that a real,
  server-backed project/repo configuration ("configure a repo for first setup", repo-view redesign) remains
  a separate, later feature; here EXIT simply returns to that existing step.
- **Single operator** (per 016). Multi-user display, avatars from a real identity, and redirect-on-session-
  expiry are out of scope.
- **Theming/light-mode** (another item on the UX list) is unrelated and out of scope here.
