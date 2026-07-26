# Tasks: Typed Delivery-Step Wrappers (render/deploy/uat)

**Feature**: 026-typed-delivery-steps | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Model & executor

- [X] T001 `HestiaTool` enum (`RENDER`→zosn/render, `DEPLOY`→logos/deploy, `UAT`→euri/test) +
  `HestiaStep(tool, args)` data class with `render`/`deploy`/`uat` factories.
- [X] T002 `HestiaStepExecutor` (one plugin for the family): `supports(HestiaStep)`; pure
  `argv = <binary> <subcommand> <args…>`; sandbox-aware (024 isolation).
- [X] T003 Register `HestiaStepExecutor(sandbox)` in `PipelineEngine.default`.

## DSL & descriptor

- [X] T004 DSL: `renderStep`/`deployStep`/`uatStep` `Group` extensions (vararg args + shared options).
- [X] T005 Descriptor: add `render`/`deploy`/`uat` to `DEFINITION_KEYS`; `parseHestia` reads
  `{ args: [ … ] }` strictly (unknown nested key rejected) into the right `HestiaStep`.

## Tests

- [X] T006 `HestiaStepExecutorTest`: argv for render/deploy/uat (incl. no-args); missing binary → FAILED
  naming the tool.
- [X] T007 `HestiaStepDslTest`: descriptor `render`/`deploy`/`uat` == DSL models; each maps to the right
  tool; an unknown nested key is rejected by the strict parser.

## Docs

- [X] T008 `docs/getting-started.md`, `docs/roadmap.md`: the khorum delivery steps are real (026).

## Verification

- [X] T009 `:engine:test :engine:detekt :engine:koverVerify -Pdependency.env=public` green; no new
  dependency.
