# Tasks: Project Registry & Real Entry-Screen Project Picker

**Feature**: 032-project-registry | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Server

- [X] T001 `ProjectStore` (new): file-backed registry — each project at `<dir>/<name>.yml`, active name in
  `.active`; `list/exists/get/save/activeName/setActive`; `isValidName` safe-slug guard (no path traversal).
- [X] T002 `ProjectController` (new): `GET /api/projects` (seed `default` from the descriptor on first use);
  `POST /api/projects` (slug 400 / duplicate 409 / strict-parser 400, else store); `POST
  /api/projects/{name}/activate` (write text to live descriptor + set active; 404 unknown).
- [X] T003 `ServerConfig`: `projectStore` bean under `<kontinuance.store>/projects` (overridable, mirrors the
  run/log stores).
- [X] T004 `ConfigController` (027): on a successful descriptor edit, sync the active project's stored snapshot.
- [X] T005 `ProjectStoreTest` (new): save/get round-trip, list sorted, replace, active marker, slug validation.
- [X] T006 `ProjectControllerIT` (new): `@SpringBootTest` RANDOM_PORT + `WebTestClient` — GET seeds default,
  POST validates / 409 / 400×2 / malformed, activate 200 (writes descriptor) / 404.

## Web

- [X] T007 `types.ts`: `Project` + `ProjectsResponse`.
- [X] T008 `client.ts`: `getProjects` / `addProject` / `activateProject` (+ shared `postJson`).
- [X] T009 `Login.svelte`: step 2 becomes a real project picker — fetch `/api/projects`, add panel (name +
  `descriptor source` textarea, inline server-side validation error), click a project to activate + enter.
- [X] T010 `e2e/mock.ts`: `mockProjects` (list + add + activate, seeded); `enterApp` activates a project.
  `app.spec.ts`: entry-flow tests rewritten (list + add + inline-reject + open-mode) against the picker.

## Docs

- [X] T011 `docs/getting-started.md` + `docs/roadmap.md`: real projects on the entry screen (032).

## Verification

- [X] T012 `:server:test :server:detekt :server:koverVerify -Pdependency.env=public` green; web
  `svelte-check`, Vitest, Playwright green.
