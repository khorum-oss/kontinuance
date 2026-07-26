# Feature Specification: Surface the GitHub Event Source in the UI

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-26

**Status**: Draft

**Input**: User: "Surface GitHub source in UI." The GitHub event source (003) runs as a standalone
`kontinuance-ci` CLI that polls repositories, triggers runs, and posts commit-status checks — but the
dashboard can only see the *runs* it produces (via the shared run store). Its **configuration** (which repos
and pipelines it watches, the poll cadence) and its **poll cursor state** (the last-seen commit per PR/branch)
are invisible. This adds a read-only **Source** screen that shows them, alongside the GitHub-triggered runs,
so the event-source side is observable from the UI.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See what the event source watches (Priority: P1)

An operator opens the **Source** screen and sees the configured GitHub event source: the repositories it
watches, each one's PR/push pipelines and tracked branch, the poll interval, and the API base URL.

**Why this priority**: You cannot trust automation you cannot see. Showing the watched repos and cadence is
the core of making the headless poller observable.

**Independent Test**: With an event-source config present, the Source screen lists the watched repositories
and the poll cadence.

**Acceptance Scenarios**:

1. **Given** an event-source config, **When** the operator opens Source, **Then** each watched repository is
   listed with its PR pipeline, optional push pipeline, and tracked branch, plus the poll interval and base URL.
2. **Given** no event-source config is set, **When** the operator opens Source, **Then** an honest "not
   configured" state explains how to point the server at the event-source config.

---

### User Story 2 - See where polling is caught up (Priority: P1)

The operator sees the poll **cursor** state: for each watched PR and tracked branch, the last commit SHA the
event source has already processed — so they can tell it is keeping up.

**Why this priority**: The cursor is the event source's "position." Showing it turns "is it stuck?" from a log
dive into a glance.

**Independent Test**: With cursor state on disk, the Source screen shows each PR/branch key and its last-seen
SHA.

**Acceptance Scenarios**:

1. **Given** persisted poll cursors, **When** the operator opens Source, **Then** each cursor (a PR or a
   tracked branch) is shown with its last-processed commit SHA.
2. **Given** no cursor state yet, **When** the operator opens Source, **Then** an honest empty state says the
   event source has not recorded a position yet.

---

### User Story 3 - See the runs the event source triggered (Priority: P2)

The operator sees the runs that came from GitHub (pull-request, push, or manual event-source triggers),
distinct from runs triggered by hand in the UI, each with its repo and commit.

**Why this priority**: Closing the loop — config and cursors explain *what/where*; the runs show *what
happened*.

**Independent Test**: With event-source runs in the store, the Source screen lists them with repo + commit +
status.

**Acceptance Scenarios**:

1. **Given** runs triggered by the event source, **When** the operator opens Source, **Then** those runs are
   listed with their repository, short commit, status, and trigger kind — and UI-triggered ("manual") runs are
   not mixed in.

### Edge Cases

- **Token safety**: the screen and API MUST show only the **name** of the token env var (e.g. `GITHUB_TOKEN`),
  never a token value (the config never stores the token itself — only the env-var name).
- **Config present but unreadable/invalid**: surfaces as a clear error state, not a crash.
- **Read-only**: this feature only *shows* the event source; it does not start/stop or reconfigure it (the CLI
  remains the runtime). No liveness/heartbeat exists yet, so the screen does not claim the poller is "running";
  it shows configuration + last-known position + produced runs.
- **Cursor keys**: shown legibly — a PR cursor (`owner/name#pr-N`) and a tracked-branch cursor
  (`owner/name#push-branch`) are distinguished.

## Requirements *(mandatory)*

- **FR-001**: The server MUST expose a read-only `GET /api/source` reporting whether a GitHub event source is
  configured and, when it is: the poll interval, base URL, token env-var **name**, and the watched
  repositories (slug, PR pipeline, optional push pipeline, tracked branch).
- **FR-002**: `GET /api/source` MUST include the poll **cursor** state (each PR/branch key and its last-seen
  commit SHA) read from the event source's persisted cursor store.
- **FR-003**: The API MUST NOT expose any token value — only the configured env-var name.
- **FR-004**: The UI MUST present a **Source** screen (reachable from the sidebar) showing the watched
  repositories + cadence, the cursor state, and the GitHub-triggered runs (distinguished from UI-manual runs),
  with honest empty/not-configured states.
- **FR-005**: The feature MUST be read-only and MUST NOT change how the event source runs; it reads the same
  on-disk config + cursor + run store the `kontinuance-ci` CLI uses.
- **FR-006**: The change MUST introduce no new external dependency (the server reuses the event source's own
  config parser from the existing `github` module and the JDK for the cursor file).

## Success Criteria *(mandatory)*

- **SC-001**: An operator can see, in the UI, which repositories the event source watches and its poll cadence.
- **SC-002**: An operator can see each PR/branch cursor's last-processed commit.
- **SC-003**: An operator can see the GitHub-triggered runs with repo + commit, separate from manual UI runs.
- **SC-004**: No token value is ever exposed; a missing config shows an honest not-configured state.
- **SC-005**: No new external dependency; existing suites stay green; the event source's runtime is unchanged.

## Assumptions

- **Read shared on-disk state.** The event source already writes runs to the shared run store the server reads;
  this feature additionally reads its **config YAML** (via the `github` module's own `EventSourceConfig`
  loader) and its **cursor properties file** — both pointed at by server properties
  (`kontinuance.github.config`, `kontinuance.github.cursors`, the latter defaulting to the CLI's
  `~/.kontinuance/github-cursors.properties`). No process coupling; the CLI keeps running as-is.
- **Observability, not control.** Scope is a read-only status view. Starting/stopping/reconfiguring the poller,
  a liveness heartbeat, and live check-state mirroring are out of scope (future work).
- **Runs distinguish source.** Event-source runs carry an uppercase trigger kind (`PULL_REQUEST` / `PUSH` /
  `MANUAL`) while UI-triggered runs use `manual`; the Source screen filters on that.
