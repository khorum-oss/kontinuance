# Implementation Plan: Project Registry & Real Entry-Screen Project Picker

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/032-project-registry/spec.md`

## Summary

Add a file-backed named-descriptor registry to the server and make the entry screen's second step a real
project picker. `ProjectStore` keeps each project as one descriptor's text at `<store>/projects/<name>.yml`
with the active name in `.active`. `ProjectController` serves `GET /api/projects` (seeding a `default` from
the current descriptor on first use), `POST /api/projects` (name-slug + strict-parser validation, `409` on
duplicate), and `POST /api/projects/{name}/activate` (writes the project's text to the live descriptor file
and records it active). `ConfigController` (027) keeps the active project's snapshot in sync when the
descriptor is edited. The web `Login.svelte` step 2 fetches `/api/projects`, adds a project (name +
descriptor textarea, validated server-side), and activates one by clicking it. Additive; no new dependency.

## Technical Context

**Language/Version**: Kotlin/JDK 21 + Spring WebFlux (`:server`) + Svelte 5 (`web`).

**Primary Dependencies**: none new — the engine's `PipelineDescriptor` parser validates descriptors;
kotlinx-serialization builds/reads the JSON; the store is plain `java.nio.file`.

**Storage**: file-backed under `<kontinuance.store>/projects` (mirrors the run/log stores), created on demand.

**Testing**: `ProjectStoreTest` (unit: save/get/list/exists/active + slug validation) and `ProjectControllerIT`
(`@SpringBootTest` RANDOM_PORT + `WebTestClient`: GET seeds default, POST validates / 409-duplicate /
400-invalid-name / 400-unparseable, activate 200 / 404) over a temp descriptor + temp store. Web:
`svelte-check`, Vitest, and Playwright entry-flow E2E rewritten for the project picker (list + add + activate,
inline validation error).

**Constraints**: additive; names are safe slugs (no path traversal); activation writes the live descriptor;
edits sync the active project's snapshot; no new dependency.

**Scale/Scope**: server `projects/ProjectStore.kt` + `projects/ProjectController.kt` (new), `ServerConfig.kt`
(bean), `config/ConfigController.kt` (sync-on-save); web `api/types.ts` + `api/client.ts` (projects surface),
`components/Login.svelte` (picker rewrite), `e2e/mock.ts` + `app.spec.ts`; docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive `/api/projects` surface; the existing
  `/api/config`, trigger, and run contracts are unchanged (activation just repoints the descriptor file the
  server already reads).
- **II. Test-First & Integration-Verified**: PASS — the store is unit-tested and the controller is E2E-tested
  over a real HTTP round-trip; the entry flow is Playwright-tested.
- **III. Quality Gates**: PASS — detekt/Kover on `:server`; svelte-check + Vitest + Playwright on `web`.
- **IV. Code Generation**: N/A — no `@GeneratedDsl` change.
- **V. Supply-Chain Integrity**: PASS — no new dependency; `gradle/verification-metadata.xml` untouched.

No violations → Complexity Tracking empty.

## Project Structure

```text
server/.../projects/ProjectStore.kt            # NEW — file-backed named-descriptor registry + active marker
server/.../projects/ProjectController.kt        # NEW — GET list (+seed) / POST create (validate) / activate
server/.../ServerConfig.kt                      # EDIT — projectStore bean under <store>/projects
server/.../config/ConfigController.kt           # EDIT — sync active project snapshot on descriptor edit
server/.../projects/ProjectStoreTest.kt (test)  # NEW — store unit tests
server/.../projects/ProjectControllerIT.kt (test) # NEW — @SpringBootTest HTTP round-trip
web/src/lib/api/types.ts                        # EDIT — Project + ProjectsResponse
web/src/lib/api/client.ts                       # EDIT — getProjects / addProject / activateProject
web/src/lib/components/Login.svelte             # EDIT — step 2 becomes a real project picker
web/e2e/mock.ts + app.spec.ts                   # EDIT — /api/projects mock + entry-flow tests
docs/getting-started.md + docs/roadmap.md       # EDIT — projects on the entry screen (032)
```

**Structure Decision**: Activation **copies** the project's text into the existing live descriptor file rather
than threading a "current project" concept through the trigger and approval paths — the lowest-coupling way to
make selection real, so those subsystems are untouched. The store mirrors the run/log store conventions
(file-backed, overridable bean) so a database backend can replace it behind the same surface. `ConfigController`
syncs the active project's snapshot on save to close the edit-then-switch-loses-edits footgun.

## Complexity Tracking

> No Constitution Check violations — no entries.
