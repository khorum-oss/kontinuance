# engine

The v0 foundation of the Kontinuance pipeline platform: define a pipeline with a
declarative **YAML descriptor** or an equivalent **Kotlin DSL**, and execute it
**in a single local process**. Stateless — no Docker, persistence, webhooks, or UI
(deferred to v1+).

## What it does

- Parses a pipeline from YAML (`PipelineDescriptor`) or builds it with the Kotlin DSL
  (`pipeline { stage { step { run(...) } } }`); both produce the **same** model.
- Runs stages and steps in declared order with structured concurrency, launching shell
  commands via `ProcessBuilder`.
- Isolates each step in a fresh temp working directory with a **scoped environment**
  (the inherited process environment is not leaked) and cleans both up on completion.
- Enforces a per-step **timeout** (terminating the whole process tree) and a
  **concurrency cap** (`Semaphore`-gated step launches).
- Streams per-step logs to stdout through a **secret-masking** filter, so registered
  secrets never appear unmasked.
- Tracks lifecycle through an explicit sealed `PipelineStatus` model and publishes
  transitions as observable `StatusEvent`s.

## Usage

### From a YAML descriptor

```yaml
# build-and-test.yaml
pipeline:
  name: "build-and-test"
  concurrency: 2
  stages:
    - name: "build"
      steps:
        - name: "compile"
          run: "./gradlew build"
          timeout: "5m"
    - name: "test"
      steps:
        - name: "unit"
          run: "./gradlew test"
          secrets: ["TOKEN"]
```

```kotlin
val pipeline = PipelineDescriptor.load(Path.of("build-and-test.yaml"))
val run = PipelineEngine.default().run(pipeline)
println(run.status) // Success
```

### From the Kotlin DSL

```kotlin
val pipeline = pipeline("build-and-test") {
    concurrency = 2
    stage("build") {
        step("compile") {
            run("./gradlew build")
            timeout = 5.minutes
        }
    }
    stage("test") {
        step("unit") {
            run("./gradlew test")
            secrets("TOKEN")
        }
    }
}
val run = PipelineEngine.default().run(pipeline)
```

The DSL and the descriptor are guaranteed to produce equal models, so they execute with
identical ordering and final status.

## Extending step types

Execution is dispatched by `StepDefinition` type through a `StepExecutor` registry
(the step-type seam, FR-016). v0 ships one executor — `RunStepExecutor` for `RunStep`.
Adding a step type (e.g. a future `GradleStep`/`DockerStep`) means adding a sealed
`StepDefinition` subtype and a matching `StepExecutor`; the engine's stage/step loop is
unchanged.

## Package layout

| Package      | Responsibility                                                        |
|--------------|-----------------------------------------------------------------------|
| `model`      | `Pipeline`, `Stage`, `Step`, `Run`, sealed `PipelineStatus`, secrets  |
| `descriptor` | YAML descriptor parsing → model (strict validation)                   |
| `dsl`        | Kotlin DSL escape hatch → model                                       |
| `execution`  | coroutine engine, `ProcessBuilder` step runner, timeout, concurrency  |
| `logging`    | stdout log streaming + secret masking                                 |
| `secret`     | secret abstraction (v0 env-var backing)                               |

## Build & test

```bash
./gradlew :engine:build   # compile + detekt + Kover + tests
./gradlew :engine:test
```

See `specs/001-pipeline-foundation/` for the full spec, plan, and contracts.
