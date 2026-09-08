# Feature Specification: Embedded SQLite Run Store

**Feature Branch**: `claude/pipeline-sync-visibility-4tm61c`

**Created**: 2026-09-08

**Status**: Built

**Input**: User: "Is there a way to setup a light database to this? I need to be able to persist data
between sessions."

Run history has been durable since 006 — one JSON file per run, one log file per run, under
`KONTINUANCE_STORE` — and the deployment already mounts that directory on a volume, so nothing was
actually being lost across restarts. What the file layout does not give is the properties a store
earns by being a database: a write that is atomic rather than a file that can be caught half-written, a
listing that is an index scan rather than `stat` on every file in the directory followed by a JSON parse
of the newest N, and `project` as something the server can query rather than a field the browser filters
on after loading every recent run (039).

This feature adds an embedded SQLite backend behind the existing `RunStore` and `RunLogStore` seams —
the swap 006 and 018 were written to allow — and makes it the default, importing an existing file
history on first start so turning it on is not an event.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The history moves into a database without anyone migrating it (Priority: P1)

An operator upgrades a running deployment. On the next start their whole run history is in
`kontinuance.db` and the dashboard shows exactly what it showed before.

**Why this priority**: A durability change that asks the operator to run a migration step, or that
appears to lose history until they find the import command, is worse than the file layout it replaces.

**Acceptance Scenarios**:

1. **Given** a state directory holding file-backed runs and logs, **When** the server starts on the
   SQLite backend, **Then** every run and every recorded line is readable from the database.
2. **Given** that import has happened, **When** the server restarts, **Then** nothing is imported again
   and no run is duplicated.
3. **Given** a run recorded into the database after the import, **When** the server restarts, **Then**
   the stale file copy of that run does not overwrite it.
4. **Given** the import has run, **When** the operator sets the backend back to `file`, **Then** the
   original files are still there and still served — the switch is reversible.

---

### User Story 2 - One store, whichever process writes it (Priority: P1)

The server and the standalone `kontinuance-ci` CLI share a state directory. Whatever the poller records
appears in the dashboard, as it always has.

**Why this priority**: The two processes choosing backends independently is the one way this change can
lose data in practice — the CLI writing JSON files the server no longer reads would make poller-recorded
runs silently absent from the dashboard, with nothing failing to point at it.

**Acceptance Scenarios**:

1. **Given** `KONTINUANCE_STORE` and `KONTINUANCE_STORE_BACKEND` set for the deployment, **When** either
   the server or the CLI opens the history, **Then** both resolve the same directory and the same
   backend, because both open it through the one factory.
2. **Given** a misspelled backend name, **When** either process starts, **Then** it fails with a message
   naming the bad value and the valid ones, rather than serving an empty history.

## Requirements *(mandatory)*

- **FR-001**: A SQLite-backed `RunStore` and `RunLogStore` MUST satisfy the same contract as the file
  backends, verified by one shared test suite run against both — including durability across a restart,
  replace-by-id, newest-write-first ordering, and full round-trip of every field including the nested
  stage breakdown and the absence of optional ones.
- **FR-002**: Listing order MUST match the file store's — newest write first — so a run that starts and
  later finishes rises to the top, and 039's "the first record for a project is its latest run" holds.
  It MUST NOT depend on the host clock's resolution; the database orders on a counter the write advances.
- **FR-003**: Concurrent appends to one run's log MUST each produce exactly one whole line, the guarantee
  the file backend gets from a per-id lock.
- **FR-004**: The schema MUST be versioned and migrated forward on open, so a database written by an
  older build keeps working with no manual step.
- **FR-005**: The backend MUST be selectable by configuration, default to SQLite, and reject an
  unrecognised name at startup.
- **FR-006**: Opening SQLite over a directory holding a file history MUST import it once, only when the
  database is empty, and MUST NOT delete or modify any file.
- **FR-007** (from 006, still owed): One unreadable record MUST NOT take the listing down with it.
- **FR-008**: A new artifact MUST come with a dependency-verification entry; verification stays enabled.

## Success Criteria *(mandatory)*

- **SC-001**: An existing deployment upgrades with its full history intact and no operator action.
- **SC-002**: Both backends pass one contract suite; neither has behaviour the other lacks.
- **SC-003**: The database is a single file on the volume already mounted — backed up by copying it,
  inspected with the stock `sqlite3` CLI.
- **SC-004**: `./gradlew check` stays green with dependency verification enabled.

## Assumptions

- **SQLite over H2 or a server database.** SQLite is one file with no process to run, no port, and no
  credential — the same operational footprint as the directory it replaces, which is the whole appeal at
  homelab scale. Its file is readable by a tool the operator already has, which matters for a CI system
  whose job is telling you what happened. H2 is a smaller jar but stores in a format only H2 reads;
  Postgres would add a service, a credential, and a backup story to a project that deliberately has none.
- **Connection per operation, not a pool.** A handful of writes per run and a query per API call do not
  justify pool configuration. WAL journaling keeps a reader from blocking the writer, and a `busy_timeout`
  makes a contended write wait rather than fail.
- **`synchronous = FULL`.** Durability is the point; a run that reported success must still be there after
  the host loses power. The write rate is far too low for the throughput cost to matter.
- **The stage breakdown stays JSON in a column.** It is only ever read back whole with its run, so a
  second table would add a join to every listing and buy nothing. The scalars the server actually queries
  — project, status, ordering — are real columns.
- **Projects and event-source config stay files.** Descriptors are YAML documents an operator edits and
  the engine parses, not rows to query; they live on the same volume and are covered by the same backup.

## Out of Scope

- Moving the project registry, GitHub source config, or the session registry into the database.
- A server-side `project` query parameter on the runs endpoint. The indexed column makes it cheap to add,
  but the dashboard's client-side scoping (039) is unchanged here.
- Retention, pruning, or archival of old runs.
- Any database that runs as a separate process.
