# Class Diagram — v0 Engine Model

The runtime model both front-ends produce and the engine consumes. Note the
**step-type extensibility seam**: `StepDefinition` is a sealed hierarchy and
`StepExecutor` dispatches by type. v0 shipped one type (`RunStep`); feature 002 adds
`GradleStep`/`DockerStep`/`NpmStep` and their executors **additively** — every command
executor shares the `ProcessStepExecutor` base (identical isolation, timeout, masking,
and status), and the engine's core loop is unchanged.

```mermaid
classDiagram
    class Pipeline {
      +String name
      +int concurrency
      +List~Stage~ stages
    }
    class Stage {
      +String name
      +List~Step~ steps
    }
    class Step {
      +String name
      +StepDefinition definition
      +Duration timeout
      +List~SecretRef~ secrets
    }
    class StepDefinition {
      <<sealed>>
    }
    class RunStep {
      +String command
    }
    class GradleStep {
      +List~String~ tasks
      +List~String~ args
      +Boolean useWrapper
    }
    class DockerStep {
      +DockerMode mode
      +String image
      +List~String~ command
      +String context
      +List~String~ tags
    }
    class NpmStep {
      +NpmMode mode
      +String script
      +Boolean clean
    }
    class Run {
      +RunId id
      +PipelineStatus status
      +int concurrencyLimit
    }
    class StageRun
    class StepRun {
      +PipelineStatus status
      +int exitCode
    }
    class PipelineStatus {
      <<sealed>>
    }
    class PipelineEngine {
      <<interface>>
      +run(Pipeline, SecretSource) Run
      +statuses(RunId) Flow~StatusEvent~
      +cancel(RunId)
    }
    class StepExecutor {
      <<interface>>
      +supports(StepDefinition) Boolean
      +execute(StepContext) StepRun
    }
    class RunStepExecutor
    class SecretSource {
      <<interface>>
      +resolve(String) String
    }
    class EnvSecretSource
    class SecretMasker {
      +mask(String) String
    }
    StepDefinition <|-- RunStep
    StepExecutor <|.. RunStepExecutor
    SecretSource <|.. EnvSecretSource
    Pipeline "1" o-- "*" Stage
    Stage "1" o-- "*" Step
    Step "1" --> "1" StepDefinition
    Run "1" --> "1" Pipeline
    Run "1" o-- "*" StageRun
    StageRun "1" o-- "*" StepRun
    StepRun "1" --> "1" PipelineStatus
    PipelineEngine ..> StepExecutor : dispatches by type
    RunStepExecutor ..> SecretMasker : masks logs
    RunStepExecutor ..> SecretSource : resolves
```

Related: [`data-model.md`](../../specs/001-pipeline-foundation/data-model.md).
