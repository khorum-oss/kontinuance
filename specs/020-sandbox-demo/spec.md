# Feature Specification: Sandbox Demo — Build & Test a Real App

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-20

**Status**: Draft

**Input**: User description: "The demo mode is fine as an option, but I need a real example of running an application against Kontinuance — a sandbox module with a simple Gradle app that it builds and runs some tests for. I need to show it all happen fresh, based on a local run."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Watch a real build & test happen fresh, locally (Priority: P1)

An evaluator runs a single command and watches Kontinuance check out a real application, build it, and run
its tests — with the actual command output streaming — finishing green, all from a clean local run.

**Why this priority**: The shipped demo uses `echo` steps; it proves the engine/approval flow but never
builds real code. A concrete, runnable example is what makes Kontinuance believable — "it actually builds
and tests an app."

**Independent Test**: From a fresh checkout, run the demo command; observe a checkout, a build, and a test
run each producing real output, ending in success with a zero exit — no manual setup between runs.

**Acceptance Scenarios**:

1. **Given** a fresh local environment, **When** the evaluator runs the demo, **Then** Kontinuance checks the
   app out into a clean workspace, builds it, runs its tests, and reports success.
2. **Given** the demo run, **When** each step executes, **Then** its real command output (the build log, the
   test results) is shown, attributed to the step.
3. **Given** two consecutive runs, **When** the second runs, **Then** it starts from a clean checkout with no
   state carried over from the first.

---

### User Story 2 - See a regression turn the pipeline red (Priority: P2)

The evaluator breaks a test (or forces a failure) and re-runs, and the pipeline fails at the test step,
naming it — proving the tests actually gate.

**Why this priority**: A demo that can only go green doesn't prove the tests matter. Showing the red path
establishes that Kontinuance surfaces real failures.

**Independent Test**: Introduce a failing assertion (or set the provided failure flag) and run; the test step
and the overall run report failure.

**Acceptance Scenarios**:

1. **Given** a broken check, **When** the pipeline runs, **Then** the test step fails and the run finishes in
   a failure state naming the failing step (non-zero exit).

---

### User Story 3 - Run the same example through the web UI (Priority: P3)

The evaluator points the server at the example pipeline, triggers it from the dashboard, and watches the
real step logs stream in the run detail.

**Why this priority**: Ties the example to the observable UI (and the just-shipped real logs), but the
headline value is the local run; the UI path is a bonus that reuses the same pipeline.

**Independent Test**: Configure the server with the example descriptor, trigger a run from the UI, and see
the checkout/build/test output in the run's log panel.

**Acceptance Scenarios**:

1. **Given** the server configured with the example pipeline, **When** a run is triggered from the UI, **Then**
   its real build/test output appears in the run detail.

### Edge Cases

- **Fully offline / deterministic**: the example app has no external dependencies, so the build and tests
  need no network and produce the same result every time.
- **No leftover state**: each run checks the app out into a fresh, ephemeral workspace that is removed at the
  end; consecutive runs do not interfere.
- **The example is not part of the platform build**: it is a target application, not a Kontinuance module, so
  it does not affect Kontinuance's own build/tests/quality gates.
- **Toolchain assumption**: the example builds with the system build tool on the PATH (it deliberately ships
  no wrapper), demonstrating that fallback; the demo documents this prerequisite.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST include a small, self-contained example application with a build and a test
  that Kontinuance can run.
- **FR-002**: The example MUST build and test with **no external dependencies** (offline, deterministic).
- **FR-003**: The repository MUST include a Kontinuance pipeline that **checks the app out, builds it, and
  runs its tests** against a fresh per-run workspace.
- **FR-004**: The repository MUST provide a **single command** that runs the whole pipeline fresh locally and
  streams the real step output, usable from a clean checkout.
- **FR-005**: A regression in the example MUST cause the pipeline to finish in a failure state naming the
  failing step (a non-zero result).
- **FR-006**: The example application MUST NOT be part of Kontinuance's own build (it MUST NOT affect
  Kontinuance's modules, tests, or quality gates).
- **FR-007**: The same pipeline MUST be runnable through the server/UI (not only headless), reusing the same
  descriptor.
- **FR-008**: The feature MUST introduce no new dependency into Kontinuance itself and MUST require no change
  to the engine's public contract.

### Key Entities *(include if feature involves data)*

- **Example application**: a minimal buildable/testable project that stands in for a user's real repo.
- **Example pipeline**: a Kontinuance descriptor that checks out, builds, and tests the example.
- **Run command**: a script that produces a fresh, local, end-to-end run of the pipeline.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: One command, from a clean checkout, runs checkout → build → test and reports success with a
  zero exit.
- **SC-002**: The run streams the app's real build and test output, attributed to each step.
- **SC-003**: Two consecutive runs each start from a clean checkout with no carried-over state.
- **SC-004**: Breaking a test makes the run fail at the test step (non-zero exit) naming it.
- **SC-005**: The example builds and tests with no network access.
- **SC-006**: Kontinuance's own build, tests, and quality gates are unaffected by the example's presence.

## Assumptions

- **The existing `echo` demo stays** as the zero-prerequisite "does the engine/approval flow work" option;
  this feature adds a *real* build/test example alongside it, not a replacement.
- **A self-contained example** (no JUnit/third-party libs; a zero-dependency self-check as the "test suite")
  is used so the demo always works offline; a richer test framework is an easy later swap for a real project.
- **The build tool is on the PATH** (the example ships no wrapper, to demonstrate the system-tool fallback);
  `git` is available for the checkout. These are stated prerequisites.
- **The local run uses the engine CLI** (headless) for the fastest "watch it happen"; the server/UI path
  reuses the same descriptor and is documented as an alternative.
- **The app repo for the checkout** is supplied as a parameter (a local snapshot for the offline demo, or the
  operator's own repo/URL in practice) — the pipeline is generic over "which repo to build".
