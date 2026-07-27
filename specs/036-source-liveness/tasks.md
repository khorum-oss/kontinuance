# Tasks: Event-Source Liveness Heartbeat

**Feature**: 036-source-liveness | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## GitHub module (CLI)

- [ ] T001 `health/Heartbeat.kt` (new): `Heartbeat` interface + `NoOpHeartbeat`; `FileHeartbeat(file, now)`
  writes `lastPolledMillis` + increments `cycles` to a properties file; `HeartbeatState(lastPolledMillis,
  cycles)` + `read(file): HeartbeatState?` (null when absent/unreadable) for the server to reuse.
- [ ] T002 `EventSource`: add a `heartbeat: Heartbeat = NoOpHeartbeat` param; in `pollAndRun`, beat right
  after `poller.poll()` returns (a successful check), before running the triggered pipelines.
- [ ] T003 `EventSourceRunner`: thread a `heartbeat` through `eventSourceFrom` (default NoOp) and wire
  `FileHeartbeat(~/.kontinuance/github-heartbeat.properties)` in `main`.
- [ ] T004 `FileHeartbeatTest` (new): beat writes the injected clock time; repeated beats increment `cycles`;
  `read` round-trips; `read` of a missing file is null.
- [ ] T005 `EventSourceIT`: a `pollAndRun()` beats a recording heartbeat.

## Server

- [ ] T006 `GitHubSourceController`: read `kontinuance.github.heartbeat` (default
  `~/.kontinuance/github-heartbeat.properties`) via the module's `HeartbeatState.read`; when present add
  `heartbeat { lastPolledMillis, ageSeconds, stale, cycles }` where `stale = ageSeconds > 3 ×
  pollIntervalSeconds`; omit when absent.
- [ ] T007 `GitHubSourceControllerIT`: a fresh heartbeat → `stale:false` + cycles; an old heartbeat →
  `stale:true`; no heartbeat file → no `heartbeat` in the response.

## Web

- [ ] T008 `types.ts`: `SourceStatus.heartbeat?` (`{ lastPolledMillis, ageSeconds, stale, cycles }`).
- [ ] T009 `Source.svelte`: a liveness indicator — polling (fresh, green) / stale (red) / unknown (when no
  heartbeat) — showing time since the last poll and the cycle count.
- [ ] T010 `e2e/mock.ts`: add a heartbeat to the configured source payload. `app.spec.ts`: assert the liveness
  indicator renders.

## Docs

- [ ] T011 `docs/getting-started.md` + `docs/roadmap.md`: the Source screen shows event-source liveness (036).

## Verification

- [ ] T012 `:github:test :github:detekt :server:test :server:detekt :server:koverVerify
  -Pdependency.env=public` green; web `svelte-check`, Vitest, Playwright green.
