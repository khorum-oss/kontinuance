# Feature Specification: Arbitrary Commit-SHA Checkout

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-26

**Status**: Draft

**Input**: User: "SHA checkout (015 follow-up)." The checkout (015) clones a branch or tag only; an arbitrary
commit **SHA** is not supported. This adds it: a `git:` step (and a project's source, 033) may pin an exact
commit, which the engine fetches and checks out. It closes the caveat both 015 and 033 called out.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Check out an exact commit in a descriptor (Priority: P1)

A pipeline author pins a `git:` step to a specific commit SHA and the run checks out exactly that commit,
rather than a branch tip that can move.

**Why this priority**: Reproducible builds need an immutable ref. A branch tip drifts; a SHA is exact.

**Independent Test**: A descriptor with a `git:` step naming a commit SHA checks out that commit into the
workspace.

**Acceptance Scenarios**:

1. **Given** a `git:` step with a commit SHA, **When** the run executes it, **Then** the workspace holds that
   exact commit's tree.
2. **Given** a `git:` step, **When** it names **both** a branch/tag and a SHA, **Then** the descriptor is
   rejected with a clear message (they are mutually exclusive).

---

### User Story 2 - Pin a project's source to a commit (Priority: P1)

An operator puts a commit SHA in a project's source field (033), and a run of that project checks out that
commit — the same field that already accepts a branch/tag.

**Why this priority**: 033 made the project's source drive the checkout; pinning it to a commit is the natural
completion, and keeps one field for "what to check out."

**Independent Test**: Set a project's source to a repo + a commit SHA, trigger a run, and confirm it checks
out that commit.

**Acceptance Scenarios**:

1. **Given** a project whose source value is a commit SHA, **When** a run is triggered, **Then** the checkout
   pins that commit (not a branch).
2. **Given** a project whose source value is a branch/tag name, **When** a run is triggered, **Then** the
   checkout uses the branch/tag exactly as before.

### Edge Cases

- **What counts as a SHA**: a hex string of 7–40 characters is treated as a commit SHA; anything else is a
  branch/tag name. (Ambiguity is inherent to git; this is the conventional heuristic and is applied only where
  a single field must serve both — the project source. The descriptor keeps them as **separate** keys.)
- **Shallow depth with a SHA**: a pinned SHA still honors `depth` where the server allows fetching a commit by
  id; otherwise the run's fetch reports the failure like any other checkout error (no silent fallback).
- **Both ref and sha in a descriptor step**: rejected at parse time (mutually exclusive).
- **Unreachable/unknown SHA**: surfaces as a normal FAILED checkout step (a `git` fetch error), not a crash.

## Requirements *(mandatory)*

- **FR-001**: A `git:` step MUST accept an optional commit **SHA** (a new `sha` key) that pins the checkout to
  an exact commit; `sha` and `ref` are mutually exclusive and naming both MUST be rejected at parse time.
- **FR-002**: When a step names a SHA, the engine MUST fetch and check out that commit into the step's target
  directory (honoring `depth` where supported), inheriting the same masking/status/isolation as the existing
  clone path.
- **FR-003**: A project's source value (033) that looks like a commit SHA (hex, 7–40 chars) MUST drive a SHA
  checkout; any other value MUST drive a branch/tag checkout, and switching a project between the two MUST NOT
  leave the other set.
- **FR-004**: The Kotlin DSL `gitStep { }` MUST expose the same `sha` option, producing the identical model
  (Principle I parity with the descriptor).
- **FR-005**: The change MUST introduce no new dependency; a checkout with no SHA behaves exactly as before
  (the clone path is unchanged).

## Success Criteria *(mandatory)*

- **SC-001**: A descriptor (or DSL) `git:` step pinned to a commit SHA checks out exactly that commit.
- **SC-002**: A project whose source is a commit SHA runs against that commit; a branch/tag value still works
  as before.
- **SC-003**: Naming both a branch/tag and a SHA in one step is rejected with a clear message.
- **SC-004**: No new dependency; a checkout without a SHA is byte-for-byte the old clone behavior; existing
  suites stay green.

## Assumptions

- **Fetch-then-checkout for a SHA.** `git clone --branch` cannot name a commit, so a pinned SHA is realized by
  initializing the target dir, fetching the commit id, and checking it out (`FETCH_HEAD`). The argv is built
  with the URL/SHA/dir passed as **positional parameters** (not interpolated), so it is not subject to shell
  injection — matching the no-shell-interpolation stance of the clone path.
- **Heuristic only where one field serves both.** The descriptor keeps `ref` and `sha` as distinct keys (no
  guessing). The 7–40-hex heuristic is used only for the project source's single value (033), where the
  operator has one field for "what to check out."
- **Server-side SHA fetch support.** Fetching a commit by id relies on the remote allowing it (GitHub does);
  where it is not allowed the checkout fails visibly like any other `git` error, with no silent fallback.
