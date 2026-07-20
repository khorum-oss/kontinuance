# Feature Specification: Light/Dark Theme & Brightness

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-20

**Status**: Draft

**Input**: User description: "A way to adjust the brightness and a light mode — the UI is dark-only today."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Switch between light and dark (Priority: P1)

An operator toggles the dashboard between a dark and a light appearance; every screen — chrome, text, status
colors, the log panel — reads clearly in the chosen mode, and the choice persists across reloads.

**Why this priority**: The dashboard is dark-only, which is unusable for operators in bright environments or
who prefer light UIs. A working light mode is the headline of this feature.

**Independent Test**: Toggle to light; confirm the whole app switches to a light appearance with legible
text and status colors; reload and confirm it stays light; toggle back to dark and confirm it persists.

**Acceptance Scenarios**:

1. **Given** the app in dark mode, **When** the operator activates the light toggle, **Then** the entire UI
   switches to light and remains readable (text, borders, and status/coverage colors all legible).
2. **Given** a chosen theme, **When** the operator reloads the app, **Then** the same theme is applied on
   load without a manual re-toggle.
3. **Given** a first-time visitor with no saved choice, **When** the app loads, **Then** it follows the
   operating system's light/dark preference.

---

### User Story 2 - Adjust brightness (Priority: P2)

An operator dials the overall brightness up or down to suit their environment, and the setting persists.

**Why this priority**: Requested alongside light mode ("a way to adjust the brightness"). It complements the
theme (dim a dark room, brighten a washed-out screen) and is independent of the light/dark choice.

**Independent Test**: Move the brightness control; confirm the whole UI visibly dims/brightens; reload and
confirm the level is retained.

**Acceptance Scenarios**:

1. **Given** any theme, **When** the operator changes the brightness control, **Then** the whole UI dims or
   brightens accordingly.
2. **Given** an adjusted brightness, **When** the operator reloads, **Then** the level is retained.
3. **Given** the brightness control, **When** the operator drives it to its extremes, **Then** it is bounded
   to a sensible range (never fully black or blinding).

### Edge Cases

- **No saved preference**: fall back to the OS `prefers-color-scheme`; brightness defaults to normal (1.0).
- **Theme + brightness are independent**: changing one never resets the other.
- **Persistence is per-browser** (local to the device); it is a UI preference, not server state, so it needs
  no sign-in and does not sync across devices.
- **Native controls** (the brightness slider) match the active theme (light controls in light mode).
- **The theme applies to every view** including the sign-in/project screens, even though the control itself
  lives in the app chrome.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The UI MUST support a **light** and a **dark** appearance covering all chrome, text, status,
  and accent colors, switchable at runtime.
- **FR-002**: The chosen theme MUST persist per-browser and be re-applied on load without a manual toggle.
- **FR-003**: With no saved choice, the UI MUST follow the operating system's light/dark preference.
- **FR-004**: The UI MUST provide a **brightness** control that adjusts the overall brightness of the whole
  interface, bounded to a sensible range, and MUST persist the level per-browser.
- **FR-005**: Theme and brightness MUST be independent — changing one MUST NOT reset the other.
- **FR-006**: Status/coverage/accent colors (shown via component logic, not only CSS) MUST adapt to the
  active theme so nothing becomes illegible in light mode.
- **FR-007**: The change MUST introduce no new runtime dependency and MUST require no server change or
  authentication (it is a local UI preference).

### Key Entities *(include if feature involves data)*

- **Appearance preference**: the operator's chosen theme (light/dark) and brightness level, stored locally in
  the browser and applied to the whole UI.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Toggling to light switches the entire UI (including status colors and the log panel) to a
  legible light appearance; toggling back restores dark.
- **SC-002**: A chosen theme and brightness both survive a reload.
- **SC-003**: A first-time visitor gets the theme matching their OS preference.
- **SC-004**: The brightness control visibly changes the whole UI and cannot be driven to unusable extremes.
- **SC-005**: Changing theme leaves brightness unchanged and vice-versa.
- **SC-006**: No new dependency, no server change, no sign-in required for the preference.

## Assumptions

- **Single accent family**: the existing teal/status palette is retained; light mode re-tones those colors
  for contrast rather than introducing a new brand palette.
- **Brightness = a global visual dimmer/brightener** over the whole UI (not a separate contrast/gamma
  control); a small bounded range around normal is enough. Fine-grained accessibility contrast controls are
  out of scope.
- **The control lives in the app top bar.** Exposing it on the sign-in screen too is a nice-to-have, not
  required; the theme still applies there.
- **Preference is device-local** (browser storage), consistent with a single-operator tool; syncing a
  preference to the server/account is out of scope.
