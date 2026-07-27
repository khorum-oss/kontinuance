# Implementation Plan: Event-Source Liveness Heartbeat

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/036-source-liveness/spec.md`

## Summary

Give the headless event source a liveness signal. A new `Heartbeat` in the `github` module records the
last-poll time + a cycle count to a properties file (`~/.kontinuance/github-heartbeat.properties`);
`EventSource` beats it right after a successful `poller.poll()` (so a failing poll does not refresh it), wired
through `eventSourceFrom` / the CLI `main`. The server's `GET /api/source` (035) reads the heartbeat (reusing
the module's `HeartbeatState` parser), stamps the age and a **stale** flag (age > 3× the config poll interval),
and includes it; absent → omitted (unknown). The Source screen renders a polling / stale / unknown indicator.

## Technical Context

**Language/Version**: Kotlin/JDK 21 — `github` (CLI) + `server` (WebFlux, already depends on `:github` since
035); Svelte 5 (`web`).

**Primary Dependencies**: none new — `java.util.Properties` for the heartbeat file; the server reuses the
`github` module's `HeartbeatState` reader.

**Storage**: one new small file `~/.kontinuance/github-heartbeat.properties` (`lastPolledMillis`, `cycles`),
beside the existing cursor file.

**Testing**: `FileHeartbeatTest` (beat writes the injected clock's time + increments cycles; `read`
round-trips; missing file → null); `EventSourceIT` gains a case asserting a poll beats the heartbeat;
`GitHubSourceControllerIT` gains fresh → `stale:false`, old → `stale:true`, and no-file → no heartbeat. Web:
`svelte-check`, Vitest, Playwright (the liveness indicator + the unknown state).

**Constraints**: heartbeat on successful poll only; server-side age/stale; no new external dependency; a poller
without a heartbeat still works (server shows unknown); poll behavior otherwise unchanged.

**Scale/Scope**: `github/.../health/Heartbeat.kt` (new), `github/.../EventSource.kt` (beat on poll),
`github/.../cli/EventSourceRunner.kt` (wire the file heartbeat); `server/.../github/GitHubSourceController.kt`
(read + age/stale in `/api/source`); web `api/types.ts` + `screens/Source.svelte`; `e2e/mock.ts` +
`app.spec.ts`; docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive `heartbeat` object on `/api/source`; a new
  heartbeat file beside the cursor file; the event source's existing behavior/outputs are unchanged.
- **II. Test-First & Integration-Verified**: PASS — the heartbeat file is unit-tested, the beat-on-poll is
  covered in `EventSourceIT`, and the age/stale projection is E2E-tested in the controller.
- **III. Quality Gates**: PASS — detekt on `:github`; detekt/Kover on `:server`; svelte-check + Vitest +
  Playwright on `web`.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new external dependency (`gradle/verification-metadata.xml`
  untouched).

No violations → Complexity Tracking empty.

## Project Structure

```text
github/.../health/Heartbeat.kt                       # NEW — Heartbeat/NoOp/FileHeartbeat + HeartbeatState.read
github/.../EventSource.kt                            # EDIT — beat after a successful poller.poll()
github/.../cli/EventSourceRunner.kt                  # EDIT — wire FileHeartbeat(~/.kontinuance/…)
github/.../health/FileHeartbeatTest.kt (test)        # NEW — write/increment/round-trip/missing
github/.../EventSourceIT.kt (test)                   # EDIT — a poll beats the heartbeat
server/.../github/GitHubSourceController.kt           # EDIT — read heartbeat; age + stale in /api/source
server/.../github/GitHubSourceControllerIT.kt (test)  # EDIT — fresh/stale/absent heartbeat
web/src/lib/api/types.ts                            # EDIT — SourceStatus.heartbeat
web/src/lib/screens/Source.svelte                   # EDIT — polling / stale / unknown indicator
web/e2e/mock.ts + app.spec.ts                       # EDIT — heartbeat in the mock + assertion
docs/getting-started.md + docs/roadmap.md           # EDIT — liveness (036)
```

**Structure Decision**: Mirror the cursor store — a small properties file the CLI writes and the server reads
— so there is no process coupling and the design matches 035. Beat inside `pollAndRun` immediately after
`poller.poll()` returns (before running the triggered pipelines), so the timestamp means "last successfully
checked GitHub" and is independent of how long the triggered runs take; a poll that throws (rate limit) skips
the beat and correctly trends stale. Compute age/stale on the server (shared host/clock) so the UI only
renders. Keep the `HeartbeatState` reader in `:github` so the write and read formats live in one place, reused
by the server (which already depends on `:github`).

## Complexity Tracking

> No Constitution Check violations — no entries.
