# Feature Specification: Pipeline Execution Foundation (v0)

**Feature Branch**: `001-pipeline-foundation`

**Created**: 2026-06-23

**Status**: Draft

**Input**: User description: "Kontinuance v0 CI/CD platform foundation: define a pipeline (hybrid YAML + Kotlin DSL on the latest Konstellation), execute its stages and steps in-process via coroutines/ProcessBuilder, track lifecycle through a sealed-class status model, isolate steps, mask secrets, and stream logs to stdout. Stateless — no Docker, persistence, webhooks, or UI yet."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Define and run a pipeline from a YAML descriptor (Priority: P1)

A developer describes a pipeline in a simple YAML/TOML descriptor (stages, each with
ordered steps that run shell commands) and asks Kontinuance to execute it. The
platform parses the descriptor, runs each stage's steps in order in the local
process, and reports the outcome of the run.

**Why this priority**: This is the irreducible core of a CI/CD engine — turning a
pipeline definition into executed work with a pass/fail result. Without it nothing
else has value. It is the v0 MVP on its own.

**Independent Test**: Provide a YAML descriptor with two stages (`build`, `test`),
each running a known shell command; invoke the runner; assert the steps execute in
declared order and the run reports SUCCESS when all commands exit `0`.

**Acceptance Scenarios**:

1. **Given** a valid two-stage pipeline descriptor whose every command exits `0`,
   **When** the pipeline is executed, **Then** every step runs in declared order and
   the run's final status is SUCCESS.
2. **Given** a pipeline whose second step exits non-zero, **When** it is executed,
   **Then** that step is marked FAILED, the run's final status is FAILED naming the
   failing step, and no later steps in that stage run.
3. **Given** a descriptor that fails to parse (unknown field / malformed), **When**
   it is loaded, **Then** execution does not start and the author receives a clear
   error identifying the problem location.

---

### User Story 2 - Author a pipeline with the Kotlin DSL escape hatch (Priority: P2)

For pipelines that exceed what the YAML descriptor expresses cleanly, a developer
authors the same pipeline using an idiomatic Kotlin DSL (lambda-with-receiver,
`pipeline { stage("…") { step("…") { run("…") } } }`) built on the latest
Konstellation meta-DSL. The DSL produces the same pipeline model the YAML path
produces, so both run through one execution engine.

**Why this priority**: The hybrid (Option C) approach is a defining decision: YAML
for simple cases, Kotlin for complex ones. The escape hatch is needed for adoption
but is not required to prove the engine works, so it sits behind Story 1.

**Independent Test**: Author a pipeline equivalent to Story 1's via the Kotlin DSL;
assert it produces an equivalent pipeline model and executes with identical
step ordering and final status.

**Acceptance Scenarios**:

1. **Given** a pipeline authored with the Kotlin DSL, **When** it is built, **Then**
   it yields the same in-memory pipeline model that an equivalent YAML descriptor
   yields.
2. **Given** YAML and Kotlin definitions describing the same pipeline, **When** each
   is executed, **Then** both produce the same step ordering and final status.

---

### User Story 3 - Observe lifecycle and isolated, secret-safe logs (Priority: P3)

While a pipeline runs, the developer watches each step transition through an explicit
lifecycle (PENDING → RUNNING → terminal) and reads per-step logs streamed to stdout.
Each step runs in its own working directory with a scoped environment, gets cleaned
up on completion/failure/timeout, and any value marked secret is masked in the log
output.

**Why this priority**: Observability, isolation, and secret masking are what make the
engine trustworthy rather than a toy. They build on Stories 1–2 and harden them, so
they are P3 within v0 but explicitly in scope.

**Independent Test**: Run a pipeline whose step writes a file to its working dir and
echoes a secret-tagged variable; assert the step's status transitions are observable
in order, the working dir is isolated and removed afterward, and the secret value is
replaced by a mask in stdout.

**Acceptance Scenarios**:

1. **Given** a running step, **When** its status changes, **Then** observers see the
   transitions in order (PENDING → RUNNING → SUCCESS/FAILED/TIMED_OUT/SKIPPED).
2. **Given** two steps that write to a relative path, **When** they run, **Then**
   neither sees the other's working directory or environment mutations.
3. **Given** a step that exceeds its configured timeout, **When** the deadline
   passes, **Then** the step is terminated, its process tree is cleaned up, and it is
   marked TIMED_OUT.
4. **Given** a value registered as a secret, **When** it appears in step output,
   **Then** it is masked in the streamed logs.

### Edge Cases

- An empty pipeline (no stages) or an empty stage (no steps): completes immediately
  with a well-defined status rather than erroring ambiguously.
- A step whose command cannot be launched (executable not found): marked FAILED with
  a clear reason, not an unhandled crash.
- A conditional/disabled step: marked SKIPPED and does not block its stage.
- Concurrency cap reached: additional runnable steps queue rather than over-subscribe
  the host.
- A run cancelled mid-flight: in-flight step terminated and cleaned up; run ends
  CANCELLED.
- A secret value that also appears coincidentally in unrelated output is still masked
  wherever the secret string occurs.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST load a pipeline from a declarative YAML/TOML descriptor
  consisting of named stages, each containing ordered steps that run a shell command.
- **FR-002**: System MUST provide an equivalent Kotlin DSL (lambda-with-receiver,
  built on the latest Konstellation meta-DSL) that produces the same pipeline model
  as the descriptor; both MUST feed a single execution engine.
- **FR-003**: System MUST validate a pipeline definition before execution and reject
  malformed input with an actionable error identifying the location, without starting
  any step.
- **FR-004**: System MUST execute steps within a stage in their declared order, and
  stages in their declared order, in the local process (coroutine-coordinated,
  commands launched via `ProcessBuilder`).
- **FR-005**: System MUST stop a stage on the first failing step and MUST mark the run
  FAILED, identifying the failing step; remaining steps in that stage MUST NOT run.
- **FR-006**: System MUST represent run/stage/step lifecycle with an explicit
  sealed-class status model covering at least PENDING, QUEUED, RUNNING, SUCCESS,
  FAILED, CANCELLED, TIMED_OUT, SKIPPED, and WAITING_ON_APPROVAL, and MUST expose
  status transitions to observers.
- **FR-007**: System MUST run each step with an isolated working directory and a
  scoped environment so that one step cannot observe or mutate another's working
  directory or environment.
- **FR-008**: System MUST clean up a step's working directory and process tree on
  completion, failure, cancellation, or timeout.
- **FR-009**: System MUST enforce a per-step timeout; a step exceeding it MUST be
  terminated and marked TIMED_OUT.
- **FR-010**: System MUST stream per-step logs to stdout in real time (append-only;
  not routed through any datastore).
- **FR-011**: System MUST mask values registered as secrets wherever they appear in
  streamed log output.
- **FR-012**: System MUST source secret values through an abstraction (v0 backing:
  environment variables) so the storage backend can change later without altering the
  pipeline definition.
- **FR-013**: System MUST support a configurable limit on concurrently executing
  steps and MUST queue runnable steps beyond that limit rather than over-subscribing
  the host.
- **FR-014**: System MUST support cancellation of an in-flight run, terminating and
  cleaning up running steps and ending the run CANCELLED.
- **FR-015**: System MUST treat conditional/disabled steps as SKIPPED without failing
  their stage.
- **FR-016**: System MUST model step execution as an **extensible step-type** seam —
  the engine selects a `StepExecutor` by the step's type and dispatches to it. v0
  ships exactly one type (the shell `run` step); new step types (e.g. gradle/docker/
  npm in a later feature) MUST be addable by registering a new executor, **without
  changing the engine's stage/step execution loop**.

### Non-Goals (v0)

- Container/Docker or Kubernetes step isolation (v1+).
- Persistent state / database storage of runs (v1+).
- Webhook/polling event sources and triggering (v1+).
- Web UI and remote log streaming over SSE/WebSocket (v1+).
- Multi-agent / distributed runners, artifact storage, plugin system, caching
  (v2/v3).

### Key Entities *(include if feature involves data)*

- **Pipeline**: a named, ordered collection of stages; the unit submitted for a run.
- **Stage**: a named, ordered collection of steps within a pipeline.
- **Step**: a single unit of work that runs a shell command, with optional timeout,
  condition, and secret references.
- **Run (Execution)**: one execution of a pipeline; carries an overall status and the
  per-stage/per-step statuses and logs.
- **Status**: the explicit lifecycle state of a run/stage/step (sealed hierarchy).
- **Secret**: a named sensitive value, resolved through the secret abstraction and
  masked in logs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can take an example pipeline descriptor and run it to a
  correct SUCCESS/FAILED result with no code changes to the platform.
- **SC-002**: An identical pipeline expressed in YAML and in the Kotlin DSL produces
  the same step ordering and final status in 100% of cases.
- **SC-003**: In a run where a step echoes a registered secret, the raw secret value
  appears in stdout 0 times (always masked).
- **SC-004**: Steps are isolated such that a pipeline of N steps each writing the same
  relative filename produces no cross-step interference in 100% of runs.
- **SC-005**: A step exceeding its timeout is terminated and marked TIMED_OUT within a
  small bounded delay (target: within 1s of the deadline), leaving no orphaned
  processes.
- **SC-006**: With a concurrency limit of K, the number of simultaneously RUNNING
  steps never exceeds K.

## Assumptions

- v0 runs entirely in a single local process; no remote agents, containers, or
  database are involved (those are deferred to v1+).
- Step isolation in v0 is process/working-directory based (separate temp working
  dirs + scoped env + process-tree cleanup), not container based.
- The pipeline DSL is **Option C (hybrid)**: YAML/TOML for simple pipelines with a
  Kotlin DSL escape hatch, built on the **latest Konstellation meta-DSL** (the
  current `gradle/libs.versions.toml` pin is to be upgraded to the newest release).
- Secret values in v0 are supplied via environment variables, behind an abstraction
  that allows a future backend (e.g. Vault) without changing pipeline definitions.
- Logs in v0 go to stdout as an append-only stream; richer storage and remote
  streaming are deferred.
- The platform targets Kotlin on the JDK 21 toolchain and Spring Boot, consistent
  with the constitution and existing modules; `core-test`/geordi is the test
  framework used to verify behavior.
