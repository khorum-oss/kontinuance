# Feature 040: Connect GitHub from the UI

**Status**: Built · **Depends on**: 003 (event source), 016 (authentication), 035/036 (source view)

## Problem

Kontinuance could watch a GitHub repository since 003, but only by hand. An operator had to write an
event-source YAML, export a token into that process's environment, install and run the `kontinuance-ci`
CLI beside the server, and keep its cursor and heartbeat files somewhere the server could also read.
Features 035 and 036 made that setup *observable* in the dashboard, but not *changeable*: the Source
screen could only report what someone had already configured elsewhere.

The result was a product with a working dashboard that took a documented, multi-step, two-process
procedure before it would show a single real build. That procedure — not any missing engine capability —
was what stood between a fresh install and CI actually running.

## What changes

Watching a repository becomes a form on the Source screen. The server hosts the poll loop itself, so a
deployment runs one process instead of two.

Nothing about the *format* changes: the connect endpoint writes the same config YAML the CLI reads, in
the same place, alongside the same cursor and heartbeat files. An existing CLI deployment keeps working
unchanged, and a config written through the UI could be handed to the CLI.

## Requirements

- **FR-001**: `EventSourceConfig` can render itself to YAML that its own parser reads back to an equal
  config, so a config authored through the API is the same artifact the CLI consumes.
- **FR-002**: The server can start, stop, and replace a GitHub poll loop in-process, over the existing
  `EventSource.runForever` coroutine. Only one runs at a time.
- **FR-003**: `POST /api/source` writes the config, stores a supplied token, and starts polling.
  `DELETE /api/source` stops it; `?forget=true` also removes the config and the token.
- **FR-004**: `GET /api/source` additionally reports whether the server is polling (`running`), whether a
  token is available (`hasToken`), and whether this server can manage a source at all (`manageable`).
- **FR-005**: A connect with no resolvable token is **refused**, leaving no config behind, rather than
  starting a loop that would fail authentication on every cycle.
- **FR-006**: The write endpoints refuse on a server without operator authentication (`409`). They accept
  a credential and start outbound work under it, which an open API must not let an arbitrary caller do.
- **FR-007**: A token supplied through the API is written with owner-only permissions and is never
  returned by any read. An environment variable named by `tokenEnv` takes precedence over a stored token,
  so a deployment can keep the secret out of the server's filesystem entirely.
- **FR-008**: A stored source resumes at startup, so a server restart does not silently stop CI. This is
  defeatable (`kontinuance.github.autostart=false`).
- **FR-009**: The Source screen offers the connect form when the server can manage a source, explains why
  it cannot when authentication is off, and shows a rejected connect against the form without discarding
  what was typed.
- **FR-010**: No new dependency in any module.

## Out of scope

- **Multiple repositories through the API.** The config format holds many bindings and the poller handles
  them; the connect endpoint takes one. Watching several still means writing the config directly.
- **Webhook triggering.** Polling stays the only mode; a signature-verified webhook remains optional
  future work.
- **Validating the token against GitHub at connect time.** A bad token surfaces as a failing poll, not as
  a rejected form.
- **A GitHub App.** The Commit Status API with a PAT is unchanged from 003.

## Verification

- `EventSourceConfigTest` — render/parse round-trip, including a name containing a quote.
- `GitHubConnectIT` — connect writes a config the event source's own parser reads back; the token is
  stored `rw-------` and absent from every read; a tokenless connect is refused and leaves no config; bad
  fields and a sub-floor poll interval are rejected; stop keeps the config while forget clears it; the
  write endpoints reject an unauthenticated request.
- `app.spec.ts` (source screen) — the connect form drives an unconfigured source to a polling one; a
  rejected connect shows the server's message and keeps the typed values; stop reports "not polling" and
  disconnect returns to the unconnected state; a server without auth explains itself and shows no form.

Tests never start a real poll loop: `kontinuance.github.autostart=false` in the server's test properties,
and the ITs assert around the loop rather than through it.
