# Tasks: Run-Derived Deploy

**Feature**: 022-deploy-real | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Input**: [plan.md](./plan.md), [spec.md](./spec.md)

No new dependency; the `/api/deploy` response shape is preserved.

## Phase 1: Server (US1 + US2)

- [ ] T001 [US1] `server/.../deploy/DeployController.kt`: `/api/deploy` from `RunStore.recent(1)` — SOURCE
  node + a node per real stage (status mapped to synced/progressing/failed/awaiting/skipped/…); environment
  = stages-done / commit / state; artifacts empty; honest external `meta`. Empty flow when no runs.
- [ ] T002 [US2] Delete `stub/StubControllers.kt` (deploy no longer a stub); drop `StubFixtures.deploy()` +
  its `node()`/`artifact()` helpers; update the class doc.
- [ ] T003 Tests: `deploy/DeployControllerIT` (seeded run → SOURCE + stage nodes with real statuses, empty
  artifacts, real stages-done/commit/state); remove the deploy assertion from `StubEndpointsIT`.

## Phase 2: Web (US1 + US2)

- [ ] T004 [US1] [US2] `web/src/lib/screens/Deploy.svelte`: honest empty states — no-runs (empty flow) and
  empty artifact manifest ("no registry integrated"); relabel the delivery panel to STAGES DONE / COMMIT /
  STATE. Update `fixtures/deploy.ts` (`sampleDeploy` → run-derived) for the story.
- [ ] T005 Update `web/e2e/mock.ts` `mockDeploy` + the deploy E2E in `app.spec.ts` to the run-derived shape.

## Phase 3: Docs + verify

- [ ] T006 Docs: `docs/getting-started.md` + `docs/roadmap.md` — Deploy is run-derived; registry/ArgoCD are
  honestly external (a real integration is a later feature).
- [ ] T007 Verify: `:server:test :server:detekt -Pdependency.env=public`; web `check` + `test:unit` +
  `test:e2e`; no new dependency.
