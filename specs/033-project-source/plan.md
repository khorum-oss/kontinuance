# Implementation Plan: Per-Project Source (repo/branch) Driving Checkout

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/033-project-source/spec.md`

## Summary

Give a stored project (032) an optional **source** — a repo URL + optional branch — stored as a sidecar
`<name>.meta.json` next to its `<name>.yml`, set/edited in the UI. At manual trigger, `ProjectSourceInjector`
applies the active project's source to the parsed pipeline: it overrides the first `GitStep`'s `url` (and
`ref` when a branch is set) via `.copy`, or prepends a synthesized checkout stage when the descriptor has no
`git:` step. The project's repo is stamped onto the run record. The descriptor grammar (015 `git:` step) is
unchanged — the source is project metadata applied to the in-memory model, not a new descriptor key.

## Technical Context

**Language/Version**: Kotlin/JDK 21 + Spring WebFlux (`:server`) + Svelte 5 (`web`); engine models are
immutable data classes (`Pipeline`/`Stage`/`Step`/`GitStep`).

**Primary Dependencies**: none new — kotlinx-serialization for the sidecar JSON + API bodies; the engine's
`GitStep` model carries `url`/`ref` already.

**Storage**: file-backed — `<store>/projects/<name>.meta.json` sidecar (`{repo, branch}`), beside the existing
`<name>.yml`; absent for sourceless projects.

**Testing**: `ProjectStoreTest` gains source round-trip; `ProjectSourceInjectorTest` (unit: override first git
step, synthesize when none, no-op when sourceless, unique synthesized stage name, first-of-many override);
`ProjectControllerIT` gains source create + `/source` update + GET echo. Web: `svelte-check`, Vitest,
Playwright (add-with-source, inline edit, no-source state).

**Constraints**: no descriptor-grammar change; override the first checkout only; blank repo = no source;
commit-SHA out of scope; no new dependency.

**Scale/Scope**: server `projects/ProjectSource.kt` (new), `projects/ProjectStore.kt` (source sidecar),
`projects/ProjectController.kt` (source in GET/POST + `/source`), `trigger/ProjectSourceInjector.kt` (new),
`trigger/RunTrigger.kt` (apply + stamp), `trigger/RunLauncher.kt` (thread repo); web `api/types.ts` +
`api/client.ts`, `components/Login.svelte`, `e2e/mock.ts` + `app.spec.ts`; docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive `repo`/`branch` on the `/api/projects`
  objects + one new `/source` action; the descriptor grammar and the trigger/run contracts are unchanged
  (the injector rewrites the in-memory pipeline only).
- **II. Test-First & Integration-Verified**: PASS — the injector is unit-tested for every branch (override /
  synthesize / no-op / naming), the store round-trips the sidecar, and the controller + entry flow are
  E2E-tested.
- **III. Quality Gates**: PASS — detekt/Kover on `:server`; svelte-check + Vitest + Playwright on `web`.
- **IV. Code Generation**: N/A — no `@GeneratedDsl` change (`GitStep` already carries `url`/`ref`).
- **V. Supply-Chain Integrity**: PASS — no new dependency; `gradle/verification-metadata.xml` untouched.

No violations → Complexity Tracking empty.

## Project Structure

```text
server/.../projects/ProjectSource.kt              # NEW — {repo, branch?} value + hasRepo
server/.../projects/ProjectStore.kt                # EDIT — source(name) / saveSource(name, src) sidecar
server/.../projects/ProjectController.kt            # EDIT — repo/branch in GET + POST; POST /{name}/source
server/.../trigger/ProjectSourceInjector.kt         # NEW — apply(pipeline, source): override/synthesize/no-op
server/.../trigger/RunTrigger.kt                    # EDIT — look up active source, inject, stamp repo
server/.../trigger/RunLauncher.kt                   # EDIT — thread optional repo onto the terminal record
server/.../projects/ProjectStoreTest.kt (test)      # EDIT — source round-trip
server/.../trigger/ProjectSourceInjectorTest.kt (test) # NEW — override / synthesize / no-op / naming
server/.../projects/ProjectControllerIT.kt (test)   # EDIT — source create + /source update + GET echo
web/src/lib/api/types.ts                            # EDIT — Project.repo?/branch?
web/src/lib/api/client.ts                           # EDIT — addProject(+source) / setProjectSource
web/src/lib/components/Login.svelte                 # EDIT — add-panel source fields + per-card inline editor
web/e2e/mock.ts + app.spec.ts                       # EDIT — source in mock + entry-flow tests
docs/getting-started.md + docs/roadmap.md           # EDIT — per-project source (033)
```

**Structure Decision**: Keep the source out of the descriptor grammar and apply it to the parsed pipeline at
the single trigger seam (`RunTrigger`), so the strict parser and descriptor portability are untouched and a
sourceless project is byte-for-byte the old behavior. The injector is a pure function (`Pipeline` →
`Pipeline`) so it is exhaustively unit-testable without Spring. The store keeps the source in a sidecar beside
the descriptor text, preserving the "swappable backend" shape of 032.

## Complexity Tracking

> No Constitution Check violations — no entries.
