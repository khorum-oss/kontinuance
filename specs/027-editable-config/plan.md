# Implementation Plan: Editable Config Screen

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/027-editable-config/spec.md`

## Summary

Add a `PUT /api/config` write path beside the existing read. A `DescriptorConfigWriter` validates edited text
with the engine's strict `PipelineDescriptor.parse` and persists it to the descriptor file **only if it
parses**, returning the refreshed `/api/config` projection (via the existing `DescriptorConfigReader`); an
invalid edit is rejected `400` with the parser's message and never overwrites the file. On the web, the
Config screen gains an edit mode (textarea + save/cancel + inline validation error) wired through a new
`api.saveConfig`. Contract-preserving (only a write is added), no new dependency, and the write endpoint is
gated by the existing auth filter.

## Technical Context

**Language/Version**: Kotlin/JDK 21 + Spring Boot WebFlux (`:server`) + Svelte 5 (`web`).

**Primary Dependencies**: none new — the engine parser + kotlinx-serialization (for the `{text}` body).

**Storage**: writes the configured descriptor file (`kontinuance.config.descriptor`).

**Testing**: `ConfigWriteIT` (`@SpringBootTest(RANDOM_PORT)`, a temp descriptor via `@DynamicPropertySource`)
— valid edit → 200 + persisted + refreshed plan; invalid edit → 400 + file unchanged; malformed body → 400.
Web: `svelte-check`, Vitest, and a Playwright config-edit E2E (reject-then-save, driven by a method-aware
`mockConfig`).

**Constraints**: validate-before-write (a bad edit never clobbers a good file); `/api/config` shape
unchanged; auth-gated like other non-public endpoints; no new dependency.

**Scale/Scope**: `server/.../config/DescriptorConfigWriter.kt` (new) + `ConfigController.kt` (add PUT);
`ConfigWriteIT.kt` (new); web `client.ts` (`saveConfig`), `screens/Config.svelte` (edit mode),
`routes/config/+page.svelte` (save wiring), `e2e/mock.ts` + `e2e/app.spec.ts`; docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive `PUT` beside the existing `GET`; the
  `/api/config` response shape is unchanged.
- **II. Test-First & Integration-Verified**: PASS — validate/persist/reject is integration-tested over real
  HTTP with a temp descriptor; the UI edit flow is E2E-tested (reject + save).
- **III. Quality Gates**: PASS — detekt/Kover on `:server`; svelte-check + Vitest + Playwright on `web`.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency.

No violations → Complexity Tracking empty.

## Project Structure

```text
server/.../config/DescriptorConfigWriter.kt     # NEW — validate (engine parser) then write; refreshed json
server/.../config/ConfigController.kt            # EDIT — add PUT /api/config ({text}); 400 on invalid/malformed
server/.../config/ConfigWriteIT.kt (test)        # NEW — valid persist / invalid rejected / malformed 400
web/src/lib/api/client.ts                         # EDIT — saveConfig(text) → PUT, throws ApiError on 400
web/src/lib/screens/Config.svelte                 # EDIT — edit mode: textarea + save/cancel + inline error
web/src/routes/config/+page.svelte                # EDIT — save() wiring (update view / surface saveError)
web/e2e/mock.ts + app.spec.ts                     # EDIT — method-aware mockConfig + config-edit E2E
docs/getting-started.md, docs/roadmap.md          # EDIT — the Config screen is editable (027)
```

**Structure Decision**: Put validation in `DescriptorConfigWriter` (mirroring `DescriptorConfigReader`) so
the parser gates the write in one testable place, and the controller stays a thin transport. Keep the write
on the same file the reader/`RunTrigger` already use, so an edit takes effect on the next run with no new
wiring. The UI reuses the read projection returned by the save, so no extra fetch.

## Complexity Tracking

> No Constitution Check violations — no entries.
