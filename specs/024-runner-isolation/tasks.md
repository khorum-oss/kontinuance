# Tasks: Docker Runner Isolation (per-step image)

**Feature**: 024-runner-isolation | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Model & DSL

- [X] T001 `Step.image: String?` (nullable, non-blank when set) added to the `@GeneratedDsl` data class;
  KSP regenerates `StepDslBuilder` with an `image` var (verified in `build/generated/**/StepDsl.kt`).
- [X] T002 Descriptor: add `image` to the strict step-level `STEP_KEYS`; parse `image:` into `Step.image`
  (comment noting it is distinct from the nested `docker.run.image`).
- [X] T003 Typed DSL: add `image` to `TypedStepOptions` and map it in `configureStep` so
  `gradleStep`/`dockerStep`/… expose it; the v0 `step { image = … }` works via the generated builder.

## Isolation seam

- [X] T004 `StepSandbox` fun-interface: `wrap(baseArgv, context) → argv` + `HOST` identity.
- [X] T005 `DockerStepSandbox`: `docker run --rm -v <absWorkspace>:/workspace -w /workspace [-e SECRET…]
  <image> <argv>` when `Step.image` is set; identity otherwise; secrets forwarded **by name only**.
- [X] T006 `ProcessStepExecutor`: inject a `sandbox` (default `DockerStepSandbox`); wrap the argv before
  launch, keeping all timeout/masking/cancellation/tree-kill behavior.
- [X] T007 Thread the sandbox through the 5 process executors and `PipelineEngine.default(sandbox=…)`
  (sandbox placed before the trailing-lambda `approvalGate` param).

## Tests

- [X] T008 `DockerStepSandboxTest`: image → exact docker argv (mount/`-w`/`-e NAME`/image/base); no image →
  identity; `HOST` identity; secret value absent from the argv.
- [X] T009 `RunnerIsolationTest`: full engine path with an injected recording sandbox — a step's image + base
  argv reach the sandbox and its output is launched (Success); a host step carries a null image.
- [X] T010 `RunnerImageDescriptorTest`: `image:` parses; DSL == descriptor model; v0 `step { image = … }`;
  blank image rejected (`DescriptorException`).

## Docs

- [X] T011 `docs/getting-started.md`, `docs/roadmap.md`: per-step `image:` runner isolation (024).

## Verification

- [X] T012 `:engine:test :engine:detekt :server:test :server:detekt -Pdependency.env=public` green
  (generated DSL regenerated + built; no new dependency).
