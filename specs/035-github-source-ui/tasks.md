# Tasks: Surface the GitHub Event Source in the UI

**Feature**: 035-github-source-ui | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Server

- [ ] T001 `server/build.gradle.kts`: add `implementation(project(":github"))` (internal dep; no new external
  dependency, no cycle — `github` shares `:engine`/`:persistence` and has no Spring).
- [ ] T002 `GitHubSourceController` (new): `GET /api/source`. When `kontinuance.github.config` is set and the
  file exists, load it via `EventSourceConfig.load` and emit `configured:true` + `pollIntervalSeconds` /
  `baseUrl` / `tokenEnv` (name only) / `repositories[]` (slug, prPipeline, pushPipeline?, trackedBranch); read
  `kontinuance.github.cursors` (default `~/.kontinuance/github-cursors.properties`) via `java.util.Properties`
  → `cursors[]` (key, sha). Missing/unset config → `configured:false`; unreadable config → error. IO off the
  event loop; never emit a token value.
- [ ] T003 `GitHubSourceControllerIT` (new): configured (temp YAML + temp cursor file) returns repos +
  cursors and no token value; not-configured returns `configured:false`.

## Web

- [ ] T004 `types.ts`: `SourceStatus` / `SourceRepo` / `SourceCursor`. `client.ts`: `getSource()`.
- [ ] T005 `Sidebar.svelte`: add the `SOURCE` → `/source` nav item.
- [ ] T006 `routes/source/+page.svelte` (new): load `getSource()` + `listRuns()`, filter runs to the GitHub
  trigger kinds (`PULL_REQUEST`/`PUSH`/`MANUAL`), pass to the screen.
- [ ] T007 `screens/Source.svelte` (new): watched repos + cadence + base URL + token env name; cursor table;
  GitHub-triggered runs (repo, short sha, status, kind); honest not-configured + empty states.
- [ ] T008 `e2e/mock.ts`: `mockSource` (configured payload + cursors). `app.spec.ts`: the Source screen shows
  repos + cursors + GitHub runs, and (separately) the not-configured state.

## Docs

- [ ] T009 `docs/getting-started.md` + `docs/roadmap.md`: the read-only Source screen (035) makes the event
  source observable.

## Verification

- [ ] T010 `:server:test :server:detekt :server:koverVerify -Pdependency.env=public` green; web
  `svelte-check`, Vitest, Playwright green.
