# Tasks: Runner Isolation Options (network / pull / uid)

**Feature**: 030-runner-options | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Model & DSL

- [X] T001 `RunnerOptions` (network / `PullPolicy` / mapUser) + `Step.runner: RunnerOptions?` on the
  `@GeneratedDsl` class; `Step.init` guards that runner options require an image.
- [X] T002 Descriptor: strict `runner: { network, pull, mapUser }` block; `pull` parsed to `PullPolicy`
  (always/missing/never), unknown values rejected; `runner` added to `STEP_KEYS`.
- [X] T003 Typed DSL: `runner` added to `TypedStepOptions` and mapped in `configureStep`; the v0
  `step { runner = … }` works via the regenerated builder.

## Sandbox

- [X] T004 `DockerStepSandbox`: from `Step.runner`, emit `--network <net>`, `--pull <policy>`, and (when
  `mapUser` and a host user resolve) `-u <hostUser>`, before the mount; a portable host-uid probe (temp-file
  unix owner, no `com.sun`) supplies the id as data; `mapUser` is a no-op when it can't be resolved.

## Tests

- [X] T005 `DockerStepSandboxTest`: argv with `--network`/`--pull`/`-u`; `mapUser` no-op without a host user;
  a containerized step with no runner options is byte-for-byte the 024 wrapper.
- [X] T006 `RunnerOptionsDescriptorTest`: `runner` parses; DSL == descriptor model; unknown pull / unknown
  runner key rejected; runner-without-image rejected (the guardrail).

## Docs

- [X] T007 `docs/getting-started.md`, `docs/roadmap.md`: per-step runner options (030).

## Verification

- [X] T008 `:engine:test :engine:detekt :engine:koverVerify -Pdependency.env=public` green; no new
  dependency.
