# Tasks: Editable Config Screen

**Feature**: 027-editable-config | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Server

- [X] T001 `DescriptorConfigWriter`: `write(path, text)` → validate with `PipelineDescriptor.parse`; only if
  it parses, create parent dirs + write the file and return the refreshed `DescriptorConfigReader` JSON
  (`Written`); otherwise `Invalid(message)` with no write.
- [X] T002 `ConfigController`: add `PUT /api/config` reading `{ "text": … }` (kotlinx-serialization), running
  the writer off `Dispatchers.IO`; `200` + refreshed config on success, `400 { error }` on invalid/malformed.
- [X] T003 `ConfigWriteIT` (`@SpringBootTest(RANDOM_PORT)`, temp descriptor via `@DynamicPropertySource`):
  valid → 200 + persisted + `plan.stages` updated; invalid → 400 + file unchanged; malformed body → 400.

## Web

- [X] T004 `client.ts`: `saveConfig(text)` → `PUT /api/config` with `{text}`; resolves the refreshed `Config`,
  throws `ApiError` with the server message on 400.
- [X] T005 `Config.svelte`: edit mode — `EDIT` opens a textarea (`aria-label="descriptor source"`);
  `SAVE`/`CANCEL`; inline `saveError` (role="alert"); closes on success, stays open on rejection.
- [X] T006 `config/+page.svelte`: `save(text)` wiring — update the view on success, set `saveError` on a
  rejected edit; pass `onsave`/`saveError` to the screen.
- [X] T007 `e2e/mock.ts`: method-aware `mockConfig` — `PUT` echoes the edited text (or 400 when it contains
  `BROKEN`). `e2e/app.spec.ts`: reject-then-save config-edit flow.

## Docs

- [X] T008 `docs/getting-started.md`, `docs/roadmap.md`: the Config screen is editable (027).

## Verification

- [X] T009 `:server:test :server:detekt -Pdependency.env=public` green; web `svelte-check`, Vitest,
  Playwright green.
