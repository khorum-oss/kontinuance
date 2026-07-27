# Feature Specification: Event-Source Liveness Heartbeat

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-26

**Status**: Draft

**Input**: User: "Event-source liveness." The Source screen (035) shows the event source's *config* and *cursor
positions*, but not whether the `kontinuance-ci` poller is actually running — 035 explicitly could not claim
"is it polling?" because no liveness signal existed. This adds one: the CLI records a **heartbeat** after each
successful poll (a timestamp + a cycle count), the server reads it, and the Source screen shows "polling · last
checked N ago" or a **stale** state when the heartbeat has gone quiet.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See that the poller is alive (Priority: P1)

An operator opens the Source screen and sees, at a glance, that the event source is polling — with how long
ago it last checked GitHub and how many poll cycles it has run.

**Why this priority**: "Is my CI poller even running?" is the first question about any background poller; 035
left it unanswered.

**Independent Test**: With a recent heartbeat on disk, the Source screen shows a live "last checked N ago"
indicator.

**Acceptance Scenarios**:

1. **Given** the event source has polled recently, **When** the operator opens Source, **Then** a live
   indicator shows the time since the last poll and the poll-cycle count.
2. **Given** the event source has not polled for well beyond its interval, **When** the operator opens Source,
   **Then** the indicator shows a **stale** state (the poller may be stopped or stuck).

---

### User Story 2 - Honest when liveness is unknown (Priority: P2)

If there is no heartbeat yet (an older CLI, or the poller has never completed a cycle), the screen says
liveness is unknown rather than implying the poller is down.

**Why this priority**: A missing heartbeat is not the same as a dead poller; the UI must not cry wolf.

**Independent Test**: With config present but no heartbeat file, the screen shows an "unknown" liveness state,
not "stale".

**Acceptance Scenarios**:

1. **Given** a configured event source with no heartbeat recorded, **When** the operator opens Source, **Then**
   the screen shows liveness as unknown (no false "stale" alarm).

### Edge Cases

- **Staleness threshold**: a heartbeat older than a small multiple of the poll interval (e.g. 3×) is
  considered stale — one missed poll is normal jitter, several missed polls is a problem.
- **Heartbeat on successful poll only**: the timestamp records a completed poll of GitHub; a poll that fails
  (rate limit / outage) does not refresh it, so a rate-limited poller correctly trends toward stale.
- **Clock**: age is computed server-side against the same host the CLI writes on (they share
  `~/.kontinuance/`), so there is no browser-vs-server clock skew.
- **No config**: unchanged from 035 — a not-configured event source shows the not-configured state, with no
  heartbeat section.

## Requirements *(mandatory)*

- **FR-001**: The `kontinuance-ci` event source MUST record a durable **heartbeat** after each successful poll
  cycle — at least the last-poll timestamp and a monotonically increasing cycle count — to a file under its
  state dir.
- **FR-002**: `GET /api/source` MUST include the heartbeat when present: the last-poll time, the age (seconds
  since), a **stale** flag (age beyond a small multiple of the poll interval), and the cycle count; and MUST
  omit it when no heartbeat exists (liveness unknown).
- **FR-003**: The heartbeat MUST refresh only on a **successful** poll, so a failing/rate-limited poller trends
  toward stale rather than appearing live.
- **FR-004**: The Source screen MUST show a liveness indicator — polling (fresh), stale, or unknown — with the
  time since the last poll and the cycle count when available.
- **FR-005**: The change MUST introduce no new external dependency and MUST NOT change the event source's poll
  behavior beyond writing the heartbeat; a poller that does not write one still works (the server shows
  unknown).

## Success Criteria *(mandatory)*

- **SC-001**: An operator can tell from the Source screen whether the event source is polling and when it last
  did.
- **SC-002**: A poller that has stopped (or is failing every poll) is shown as stale within a small multiple of
  its poll interval.
- **SC-003**: A configured event source with no heartbeat shows "unknown", never a false "stale".
- **SC-004**: No new external dependency; the poller's polling/triggering/reporting behavior is otherwise
  unchanged; existing suites stay green.

## Assumptions

- **File heartbeat, like the cursor.** The heartbeat is a small file under `~/.kontinuance/` (a properties
  file, mirroring the poll-cursor store) that the CLI writes and the server reads — no process coupling, no
  network, consistent with how 035 reads shared on-disk state.
- **Server computes age/stale.** The server stamps the age and the stale decision (using the config's poll
  interval) at read time, so the UI just renders; server and CLI share a host and clock.
- **Observability, not control.** This reports liveness; starting/stopping/restarting the poller from the UI
  remains out of scope (future work).
