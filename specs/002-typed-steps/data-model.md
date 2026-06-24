# Phase 1 Data Model: Typed Step Types

Extends feature 001's `StepDefinition` sealed hierarchy with three new subtypes and a
matching executor each. No change to `Pipeline`/`Stage`/`Step`/`Run`/`PipelineStatus`.

## New StepDefinition subtypes

### GradleStep : StepDefinition
- `tasks: List<String>` — Gradle tasks (non-empty), e.g. `["build"]`.
- `args: List<String>` — extra args, e.g. `["-x","test","--no-daemon"]`.
- `useWrapper: Boolean = true` — prefer `./gradlew` when present, else system `gradle`.

### DockerStep : StepDefinition
- `mode: DockerMode` — `RUN` or `BUILD`.
- RUN: `image: String`, `command: List<String>`, `env: Map<String,String>`,
  `volumes: List<String>`.
- BUILD: `context: String = "."`, `dockerfile: String? = null`, `tags: List<String>`,
  `buildArgs: Map<String,String>`.

### NpmStep : StepDefinition
- `mode: NpmMode` — `SCRIPT` or `INSTALL`.
- SCRIPT: `script: String` → `npm run <script>`.
- INSTALL: `clean: Boolean = true` → `npm ci` (true) or `npm install` (false).

## Executors (one per type)

Each implements the 001 `StepExecutor` interface (`supports`/`execute`) and is
registered in the engine's executor registry at init:

- `GradleStepExecutor` — `supports(GradleStep)`; builds argv
  `[gradleBin] + tasks + args` (gradleBin = `./gradlew` if present & `useWrapper`).
- `DockerStepExecutor` — `supports(DockerStep)`; builds `docker run …` or
  `docker build …` argv from `mode`.
- `NpmStepExecutor` — `supports(NpmStep)`; builds `npm run <script>` / `npm ci` /
  `npm install`.

All delegate actual launch + workdir/env/timeout/masking/status to the shared
`StepContext`/`ProcessBuilder` path from 001 (identical guarantees to `RunStep`).

## Validation rules

- `GradleStep.tasks` non-empty.
- `DockerStep` RUN requires `image` + `command`; BUILD requires a resolvable `context`.
- `NpmStep` SCRIPT requires a non-empty `script`.
- A descriptor step MUST declare exactly one of `run`/`gradle`/`docker`/`npm`.
- Missing tool binary at execution → FAILED with an actionable message (not invalid
  model): a runtime concern, surfaced through the status, not a parse error.

## Relationships

```
StepDefinition (sealed, from 001)
  ├── RunStep        (001)         ← RunStepExecutor
  ├── GradleStep     (this)        ← GradleStepExecutor
  ├── DockerStep     (this)        ← DockerStepExecutor
  └── NpmStep        (this)        ← NpmStepExecutor
Engine.registry: List<StepExecutor>  (selected by supports(definition))
```

## Invariants

- Adding these subtypes + executors changes **no** engine-loop code (SC-003).
- A typed step and an equivalent `RunStep` exhibit identical isolation + masking
  behavior because both run through `StepContext` (SC-005).
