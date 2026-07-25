# Implementation Plan: Run-Derived Deploy

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-deploy-real/spec.md`

## Summary

Replace the Deploy fixture with a `DeployController` that derives `/api/deploy` from the latest run
(`RunStore.recent(1)`): a `SOURCE` node + a node per the run's real `StageRecord`s (real status mapped to a
delivery vocabulary), and an environment panel carrying real values — stages completed, the commit, and the
run's state. The artifact manifest is empty (Kontinuance runs no registry) and the web screen shows honest
"external (ArgoCD/registry)" states instead of fabricated jars/pods. The response shape (nodes/artifacts/
environment) is unchanged, so the web contract is stable. No new dependency.

## Technical Context

**Language/Version**: Kotlin/JDK 21 (`:server`) + Svelte 5 (`web`).

**Primary Dependencies**: none new — `RunStore`, kotlinx-serialization, existing web screen.

**Storage**: none new (reads the run store).

**Testing**: `DeployControllerIT` (`@SpringBootTest`, seeded run) asserting the derived shape; the deploy
assertion is removed from `StubEndpointsIT` (no longer a stub). Web: `svelte-check`, Vitest, Playwright
(deploy E2E updated to the run-derived shape).

**Constraints**: honest — no fabricated external data; response shape preserved; no new dependency.

**Scale/Scope**: `server/.../deploy/DeployController.kt` (new), delete `stub/StubControllers.kt`, drop
`StubFixtures.deploy()`, `DeployControllerIT` (new); web `Deploy.svelte` (empty states + real labels), e2e +
fixture + docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — the `/api/deploy` response shape is unchanged; only
  its source changes (fixture → real run).
- **II. Test-First & Integration-Verified**: PASS — the derivation is integration-tested over the real HTTP
  boundary with a seeded run; the web screen is E2E-tested.
- **III. Quality Gates**: PASS — detekt/Kover on `:server`; svelte-check + Vitest + Playwright on `web`.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new dependency.

No violations → Complexity Tracking empty.

## Project Structure

```text
server/.../deploy/DeployController.kt          # NEW — /api/deploy derived from RunStore.recent(1)
server/.../stub/StubControllers.kt             # DELETE — deploy is no longer a stub
server/.../stub/StubFixtures.kt                # EDIT — drop deploy() + its helpers; note deploy is real
server/.../deploy/DeployControllerIT.kt (test) # NEW — seeded run → derived shape
server/.../StubEndpointsIT.kt (test)           # EDIT — remove the deploy stub assertion

web/src/lib/screens/Deploy.svelte              # EDIT — honest empty states; real labels (STAGES DONE/COMMIT/STATE)
web/src/lib/fixtures/deploy.ts                 # EDIT — sampleDeploy → run-derived shape (story)
web/e2e/mock.ts + app.spec.ts                  # EDIT — mockDeploy + the deploy E2E to the new shape
docs/getting-started.md, docs/roadmap.md       # EDIT — Deploy is run-derived; registry/ArgoCD external
```

**Structure Decision**: Keep the `/api/deploy` contract (nodes/artifacts/environment) and change only the
*source*. The environment fields are repurposed to real run values; the web screen adds honest empty states.
A real ArgoCD/registry integration can later fill the external parts behind the same contract.

## Complexity Tracking

> No Constitution Check violations — no entries.
