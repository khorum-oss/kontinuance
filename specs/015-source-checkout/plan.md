# Implementation Plan: Source Checkout & Shared Workspace

**Branch**: `claude/source-checkout` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-source-checkout/spec.md`

## Summary

Two coupled engine changes: (1) give a run **one shared workspace** all its steps operate in (replacing
the per-step ephemeral directory), and (2) add a **`git` checkout step type** — a `git:` descriptor key
and a `gitStep` DSL builder producing the same model — that clones a repo into the workspace. Engine-only;
usable immediately through the descriptor the server already runs. No server/web change.

## Technical Context

**Language/Version**: Kotlin 2.3.21 / JDK 21 (engine module).

**Primary Dependencies**: none new. The checkout step shells out to the `git` CLI via the existing
`ProcessStepExecutor` (the same path as `run`/`gradle`/`docker`/`npm`).

**Storage**: a per-run temp workspace directory (created + removed by the engine).

**Testing**: engine unit/integration tests (`:engine:test`) — workspace sharing + cleanup, descriptor
`git:` mapping, DSL/descriptor equivalence, and the checkout argv; a real `git clone` guarded by
`assumeTrue(git on PATH)`.

**Target Platform**: any host with `git` on the PATH and a POSIX process model.

**Constraints**: no new dependency (verification untouched); the per-step→per-run isolation change is
deliberate (spec Assumptions) with host isolation, cleanup, secret masking, and env scoping preserved; no
external design link.

**Scale/Scope**: ~10–12 files in `engine` (model, executor, descriptor + DSL, engine/StepRunner edits,
tests).

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — the checkout is added to *both* front-ends
  (descriptor + DSL) yielding one model, per Principle I. The workspace-isolation change is a documented,
  justified evolution of the 001 contract (needed to build real code); it is additive to authors (existing
  pipelines keep working — they simply now share a workspace).
- **II. Test-First / Integration-Verified**: PASS — new behavior is covered by tests (workspace sharing +
  cleanup, `git:` parse, DSL equivalence, checkout argv, a guarded real clone). A tool integration (`git`)
  is exercised behind an availability assumption.
- **III. Quality Gates**: PASS — detekt/Kover run on the engine changes and must stay green.
- **IV. Code Generation**: N/A (hand-written DSL builder, like the other typed steps).
- **V. Supply-Chain**: PASS — no new dependency; verification metadata untouched.

No violations → Complexity Tracking empty.

## Project Structure

```text
engine/src/main/kotlin/org/khorum/oss/kontinuance/engine/
├── model/GitStep.kt                         # NEW — checkout step definition
├── execution/DefaultPipelineEngine.kt       # EDIT — create/remove the per-run workspace
├── execution/StepRunner.kt                  # EDIT — run steps in the shared workspace (no per-step dir)
├── execution/PipelineEngine.kt              # EDIT — register GitStepExecutor in default()
├── execution/steps/GitStepExecutor.kt       # NEW — `git clone` argv via ProcessStepExecutor
├── descriptor/PipelineDescriptor.kt         # EDIT — `git:` key + parse
└── dsl/steps/GitStepDsl.kt                  # NEW — gitStep { } builder

engine/src/test/kotlin/.../engine/execution/
├── StepIsolationTest.kt                      # EDIT — invert to shared-workspace semantics + cleanup
├── WorkspaceSharingTest.kt                   # NEW — step writes, later step reads; cleanup
└── steps/GitStepExecutorTest.kt             # NEW — argv + guarded real clone
engine/src/test/kotlin/.../engine/descriptor/TypedStepDescriptorTest.kt   # EDIT — `git:` mapping
engine/src/test/kotlin/.../engine/dsl/steps/GitStepDslTest.kt             # NEW — DSL == descriptor
```

**Structure Decision**: Additive step type through the step-type seam (like the approval gate), plus a
focused change to the engine's working-directory model. Contained to `engine`.

## Complexity Tracking

> No Constitution Check violations — no entries.
