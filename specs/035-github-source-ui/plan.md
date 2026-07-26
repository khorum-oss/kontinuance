# Implementation Plan: Surface the GitHub Event Source in the UI

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/035-github-source-ui/spec.md`

## Summary

Make the headless GitHub event source (003) observable from the dashboard. The server gains a read-only
`GET /api/source` that, when `kontinuance.github.config` points at the event-source YAML, loads it via the
`github` module's own `EventSourceConfig.load` (reused, not reimplemented) and reads the poll-cursor
properties file (`kontinuance.github.cursors`, default `~/.kontinuance/github-cursors.properties`) with the
JDK. It reports the watched repositories, poll cadence, base URL, token env-var **name** (never a value), and
each PR/branch cursor's last-seen SHA. A new **Source** screen (sidebar) shows this plus the GitHub-triggered
runs (filtered from the shared run store by trigger kind). Read-only; the CLI runtime is untouched.

## Technical Context

**Language/Version**: Kotlin/JDK 21 + Spring WebFlux (`:server`) reusing `:github`; Svelte 5 (`web`).

**Primary Dependencies**: no new **external** dependency — the server adds an internal `project(":github")`
dependency to reuse `EventSourceConfig`/`RepositoryBinding`/`RepoRef`; the cursor file is read with
`java.util.Properties`; `github` already depends only on `:engine`/`:persistence` (no Spring), so no cycle.

**Storage**: none new — reads the event source's existing config YAML + `github-cursors.properties`; runs come
from the shared `RunStore`.

**Testing**: `GitHubSourceControllerIT` (`@SpringBootTest` RANDOM_PORT + `WebTestClient`): configured (temp
YAML + temp cursor file) returns repositories + cursors and no token value; not-configured returns
`configured:false`. Web: `svelte-check`, Vitest, Playwright (Source screen: configured view, cursors,
GitHub-run list, and the not-configured state).

**Constraints**: read-only; token env-var name only (no value); honest not-configured/empty states; reuse the
canonical config parser; no new external dependency.

**Scale/Scope**: `server/build.gradle.kts` (+`:github` dep), `server/.../github/GitHubSourceController.kt`
(new); web `api/types.ts` + `api/client.ts` (+`getSource`), `routes/source/+page.svelte` (new),
`screens/Source.svelte` (new), `components/Sidebar.svelte` (+SOURCE nav), `e2e/mock.ts` + `app.spec.ts`; docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — a new additive read-only `/api/source`; no existing
  contract changes; the event source's runtime and its config/cursor formats are read as-is, not altered.
- **II. Test-First & Integration-Verified**: PASS — the controller is E2E-tested over real HTTP (configured +
  not-configured, token-safety), and the Source screen is Playwright-tested.
- **III. Quality Gates**: PASS — detekt/Kover on `:server`; svelte-check + Vitest + Playwright on `web`.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new external dependency (`gradle/verification-metadata.xml`
  untouched); only an internal module dependency is added.

No violations → Complexity Tracking empty.

## Project Structure

```text
server/build.gradle.kts                             # EDIT — implementation(project(":github"))
server/.../github/GitHubSourceController.kt          # NEW — GET /api/source (config + cursors), token-safe
web/src/lib/api/types.ts                            # EDIT — SourceStatus / SourceRepo / SourceCursor
web/src/lib/api/client.ts                           # EDIT — getSource()
web/src/routes/source/+page.svelte                  # NEW — load getSource + listRuns (filter GitHub triggers)
web/src/lib/screens/Source.svelte                   # NEW — repos + cadence + cursors + GitHub runs
web/src/lib/components/Sidebar.svelte               # EDIT — + SOURCE nav item
web/e2e/mock.ts + app.spec.ts                       # EDIT — /api/source mock + Source screen test
docs/getting-started.md + docs/roadmap.md           # EDIT — the Source screen (035)
```

**Structure Decision**: Reuse the `github` module's `EventSourceConfig` loader from the server rather than
reimplementing the YAML schema — the server takes an internal dependency on `:github` (both already share
`:persistence`; `github` has no Spring, so no cycle and no new external dependency). Keep `/api/source`
focused on config + cursors; the Source route reuses the existing `/api/runs` endpoint and filters by trigger
kind for the GitHub-run list, so no run-store logic is duplicated. Strictly read-only — the CLI stays the
event source's runtime.

## Complexity Tracking

> No Constitution Check violations — no entries.
