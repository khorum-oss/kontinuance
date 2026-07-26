# Implementation Plan: Typed Delivery-Step Wrappers (render/deploy/uat)

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/026-typed-delivery-steps/spec.md`

## Summary

Add the khorum delivery steps as first-class typed steps, mirroring the existing `gradle`/`docker`/`npm`
pattern. One `HestiaStep` model (a `HestiaTool` enum — `RENDER`→zosn, `DEPLOY`→logos, `UAT`→euri — plus a
pass-through `args` list) is fronted by one `HestiaStepExecutor` (argv = `<binary> <subcommand> <args…>`,
sandbox-aware for 024 isolation). The descriptor gains strict `render:`/`deploy:`/`uat:` keys (each
`{ args: [ … ] }`) and the DSL gains `renderStep`/`deployStep`/`uatStep`, both producing identical models.
Registered in `PipelineEngine.default`. Tools are host CLIs (no new dependency); a missing binary FAILS the
step naming it. The exact flag surface is the real tool's — `args` is verbatim pass-through — so the engine
never hard-codes a contract it can't verify.

## Technical Context

**Language/Version**: Kotlin/JDK 21 (`:engine`), konstellation KSP DSL (unaffected — no `@GeneratedDsl`
change; the new steps are hand-written DSL like `gradleStep`).

**Primary Dependencies**: none new — `zosn`/`logos`/`euri` are host CLIs.

**Storage**: none.

**Testing**: `HestiaStepExecutorTest` (pure argv for render/deploy/uat; missing-binary → FAILED naming the
tool) and `HestiaStepDslTest` (descriptor `render`/`deploy`/`uat` == DSL models; each maps to the right
tool; unknown nested key rejected).

**Constraints**: one executor per tool family (Docker/Npm precedent); strict parser keeps rejecting unknown
keys; tools are host CLIs; shared step envelope (secrets/workingDir/timeout/image) inherited; no new dep.

**Scale/Scope**: `model/HestiaStep.kt` + `execution/steps/HestiaStepExecutor.kt` + `dsl/steps/HestiaStepDsl
.kt` (new); edit `PipelineDescriptor.kt` (keys + parse) and `PipelineEngine.kt` (register); tests + docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive step types via the existing step-type seam
  (`StepDefinition` + `StepExecutor`); no existing behavior changes.
- **II. Test-First & Integration-Verified**: PASS — pure argv is unit-tested, descriptor/DSL parity is
  tested, and the missing-binary failure path is tested over the real process machinery.
- **III. Quality Gates**: PASS — detekt/Kover on `:engine`.
- **IV. Correct, Covered Code Generation**: N/A — no `@GeneratedDsl` change (the DSL functions are
  hand-written, like `gradleStep`).
- **V. Supply-Chain Integrity**: PASS — no new dependency; tools are host CLIs.

No violations → Complexity Tracking empty.

## Project Structure

```text
engine/.../model/HestiaStep.kt                       # NEW — HestiaTool enum + HestiaStep(tool, args) + factories
engine/.../execution/steps/HestiaStepExecutor.kt      # NEW — one executor; argv = <binary> <subcommand> <args>
engine/.../dsl/steps/HestiaStepDsl.kt                 # NEW — renderStep/deployStep/uatStep Group extensions
engine/.../descriptor/PipelineDescriptor.kt           # EDIT — render/deploy/uat in DEFINITION_KEYS + parseHestia
engine/.../execution/PipelineEngine.kt                # EDIT — register HestiaStepExecutor
engine/.../execution/steps/HestiaStepExecutorTest.kt (test)  # NEW — argv + missing-binary
engine/.../dsl/steps/HestiaStepDslTest.kt (test)             # NEW — descriptor/DSL parity + strict-key
docs/getting-started.md, docs/roadmap.md              # EDIT — the delivery steps are real (026)
```

**Structure Decision**: Collapse the three tools into one `HestiaStep`/`HestiaStepExecutor` (a tool enum,
exactly as `DockerStep`/`NpmStep` collapse their operations), while exposing three semantic surfaces
(`render`/`deploy`/`uat` keys and DSL functions). This is minimal, consistent, and keeps the executor a
single well-tested plugin. The tools' real flags are pass-through `args`, so the wrapper adds typed
ergonomics (naming, secrets, isolation) without inventing a flag contract.

## Complexity Tracking

> No Constitution Check violations — no entries.
