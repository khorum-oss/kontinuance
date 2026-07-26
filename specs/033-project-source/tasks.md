# Tasks: Per-Project Source (repo/branch) Driving Checkout

**Feature**: 033-project-source | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Server

- [ ] T001 `ProjectSource` (new): `{repo: String?, branch: String?}` value with `hasRepo` (repo non-blank).
- [ ] T002 `ProjectStore`: `<name>.meta.json` sidecar — `saveSource(name, source)` / `source(name):
  ProjectSource?` (null when absent/blank); `list()`/`exists` unaffected (sidecar is `.meta.json`, not `.yml`).
- [ ] T003 `ProjectSourceInjector` (new): `apply(pipeline, source): Pipeline` — no-op when source lacks a repo;
  else override the **first** `GitStep`'s `url` (and `ref` when a branch is set) via `.copy`, or prepend a
  synthesized checkout stage (unique name) when the pipeline has no `GitStep`.
- [ ] T004 `ProjectController`: include `repo`/`branch` per project in `GET`; accept optional `repo`/`branch`
  on `POST` (store as source); `POST /api/projects/{name}/source` sets/updates an existing project's source
  (404 unknown).
- [ ] T005 `RunTrigger`: look up the active project's source, `ProjectSourceInjector.apply` before launch,
  stamp the repo on the initial `Running` record and pass it through.
- [ ] T006 `RunLauncher`: thread an optional `repo` onto the terminal/failed record.
- [ ] T007 `ProjectSourceInjectorTest` (new): override-first-git, synthesize-when-none, no-op-when-sourceless,
  unique synthesized stage name, first-of-many override.
- [ ] T008 `ProjectStoreTest`: source save/read round-trip (+ absent → null).
- [ ] T009 `ProjectControllerIT`: source on create + GET echo; `/source` update 200 + 404.

## Web

- [ ] T010 `types.ts`: `Project.repo?` / `Project.branch?`. `client.ts`: `addProject(name, text, repo?,
  branch?)` + `setProjectSource(name, repo, branch)`.
- [ ] T011 `Login.svelte`: add-panel gains repo/branch inputs; each card shows its source (or an explicit
  "no source" line) and an inline source editor (repo/branch + save) that works on existing projects.
- [ ] T012 `e2e/mock.ts`: seed a project source + wire POST source into `mockProjects`. `app.spec.ts`: assert
  add-with-source, inline edit updates the card, and the no-source state.

## Docs

- [ ] T013 `docs/getting-started.md` + `docs/roadmap.md`: per-project source drives checkout (033).

## Verification

- [ ] T014 `:server:test :server:detekt :server:koverVerify -Pdependency.env=public` green; web
  `svelte-check`, Vitest, Playwright green.
