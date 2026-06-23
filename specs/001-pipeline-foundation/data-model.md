# Phase 1 Data Model: Pipeline Execution Foundation (v0)

The in-memory model both definition front-ends (YAML descriptor, Kotlin DSL) produce
and the engine consumes. No persistence in v0 — these are runtime entities.

## Entities

### Pipeline
The unit submitted for a run.
- `name: String` — non-empty, unique within a run request.
- `stages: List<Stage>` — ordered; may be empty (an empty pipeline completes SUCCESS).

### Stage
An ordered group of steps within a pipeline.
- `name: String` — non-empty, unique within its pipeline.
- `steps: List<Step>` — ordered; may be empty (an empty stage completes SUCCESS).

### Step
A single unit of work that runs a shell command.
- `name: String` — non-empty, unique within its stage.
- `command: String` (or `List<String>`) — the shell command/argv to execute.
- `timeout: Duration?` — optional per-step deadline; null ⇒ a sane platform default.
- `condition: Boolean | Expression?` — when false/unmet ⇒ step is SKIPPED.
- `secrets: List<SecretRef>` — names of secrets referenced/injected for this step.
- `workingDirHint: String?` — optional relative subdir; resolved inside the isolated
  working directory, never an absolute escape.

### Run (Execution)
One execution of a pipeline.
- `id: RunId` — unique per execution.
- `pipeline: Pipeline` — the definition being executed.
- `status: PipelineStatus` — overall status (derived from stage/step statuses).
- `stageRuns: List<StageRun>` / `stepRuns: List<StepRun>` — per-unit execution state,
  each carrying its own `PipelineStatus`, start/end timestamps, exit code (steps), and
  a reference to its log stream.
- `concurrencyLimit: Int` — K; max simultaneously RUNNING steps.

### PipelineStatus (sealed)
Explicit lifecycle for run/stage/step.
```
sealed class PipelineStatus
  object Pending
  object Queued
  object Running
  object Success
  data class Failed(val step: String?, val reason: String)
  object Cancelled
  object TimedOut
  object Skipped
  object WaitingOnApproval
```

### Secret
A named sensitive value.
- `name: String` — the reference key used in step definitions.
- value is **never** stored on the model; resolved on demand through `SecretSource`
  and masked in any log output.

## Relationships

```
Pipeline 1───* Stage 1───* Step 1───* SecretRef ──▶ Secret (resolved via SecretSource)
Run ─── references ──▶ Pipeline
Run 1───* StageRun 1───* StepRun ── each has a PipelineStatus + log stream
```

## State transitions (run/stage/step)

```
Pending → Queued → Running → Success
                            → Failed(step, reason)
                            → Cancelled
                            → TimedOut
       → Skipped (condition unmet; bypasses Running)
       → WaitingOnApproval (reserved; not triggered by any v0 step type)
```
- A stage's status derives from its steps: Failed/TimedOut/Cancelled of any step makes
  the stage Failed/… and stops subsequent steps in that stage (FR-005).
- A run's status derives from its stages by the same rule; all-Success ⇒ Success.

## Validation rules

- Names non-empty and unique within their parent scope (FR-003).
- Unknown descriptor fields / malformed input ⇒ validation error before any step runs.
- `timeout` (if present) > 0.
- `workingDirHint` MUST resolve within the step's isolated working directory.
- `concurrencyLimit` ≥ 1.

## Invariants

- Simultaneously `Running` steps ≤ `concurrencyLimit` (SC-006).
- A registered secret value never appears unmasked in any emitted log line (SC-003).
- Every step that started has its working directory and process tree cleaned up on a
  terminal status (FR-008, SC-005).
