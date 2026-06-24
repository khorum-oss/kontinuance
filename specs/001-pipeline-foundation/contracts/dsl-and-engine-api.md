# Contract: Kotlin DSL + Engine API — v0

The Kotlin front-end (Option C escape hatch, built on the latest Konstellation
meta-DSL) and the engine entry point. Both are **consumer-facing contracts**
(Constitution Principle I).

## Pipeline DSL (escape hatch)

Lambda-with-receiver builder that yields the same pipeline model as the YAML
descriptor:

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
```

- Builders: `pipeline(name) { }`, `stage(name) { }`, `step(name) { }`.
- Step config: `run(command)`, `timeout`, `when` / condition, `secrets(vararg names)`,
  `workingDir`.
- Built on the Konstellation meta-DSL primitives so it stays idiomatic and consistent
  with the existing `dsl` module.
- **Guarantee**: the model produced is identical to the equivalent descriptor's
  (FR-002, SC-002).

## Engine entry point

```kotlin
interface PipelineEngine {
    /** Executes the pipeline in-process and returns the completed Run. */
    suspend fun run(pipeline: Pipeline, secrets: SecretSource = EnvSecretSource()): Run

    /** Observe lifecycle transitions for a run as they happen. */
    fun statuses(runId: RunId): Flow<StatusEvent>

    /** Request cancellation of an in-flight run. */
    suspend fun cancel(runId: RunId)
}

data class StatusEvent(val target: Target, val status: PipelineStatus)
```

### Behavioral contract

- `run` executes stages in order and steps within a stage in order (FR-004); stops a
  stage on first failure (FR-005).
- Concurrent `Running` steps never exceed `pipeline.concurrency` (FR-013, SC-006).
- Each step executes in an isolated temp working directory with a scoped environment;
  both are cleaned up, and the process tree terminated, on any terminal status
  (FR-007, FR-008).
- A step exceeding its timeout is terminated and marked `TimedOut` within ~1s
  (FR-009, SC-005).
- `cancel` terminates in-flight steps and ends the run `Cancelled` (FR-014).
- `statuses` emits transitions in lifecycle order (FR-006).

## Step-type extension point — `StepExecutor` (FR-016)

The engine never hard-codes "run a shell command". It dispatches each step to a
registered `StepExecutor`, selected by the step's `StepDefinition` type:

```kotlin
sealed interface StepDefinition          // RunStep (v0); GradleStep/DockerStep/NpmStep later
data class RunStep(val command: String) : StepDefinition

interface StepExecutor {
    fun supports(definition: StepDefinition): Boolean
    suspend fun execute(context: StepContext): StepRun   // owns workdir, env, timeout, masking
}
```

- v0 registers exactly one executor, `RunStepExecutor` (ProcessBuilder-based).
- Adding a step type = add a `StepDefinition` subtype + a `StepExecutor` and register
  it; **the engine's stage/step loop is unchanged**. This is the seam feature 002
  (typed gradle/docker/npm steps) builds on.
- Isolation, timeout, secret resolution, and log masking are provided to executors
  via `StepContext`, so every executor inherits those guarantees uniformly.

## Secret abstraction

```kotlin
interface SecretSource { fun resolve(name: String): String? }
class EnvSecretSource : SecretSource   // v0 backing: environment variables
```

- Resolved secret values are injected into a step's scoped environment and **masked**
  wherever they appear in streamed logs (FR-011, FR-012, SC-003).
- The interface lets a future backend (e.g. Vault) replace `EnvSecretSource` without
  changing pipeline definitions.

## Logging

- Per-step stdout/stderr stream to process stdout as append-only lines, each passed
  through the secret-masking filter before emission (FR-010, FR-011). No datastore is
  involved in v0.
