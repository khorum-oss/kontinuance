# Quickstart: Pipeline Execution Foundation (v0)

Get the v0 engine building and prove a pipeline runs end-to-end — locally, in one
process, no Docker or database.

## Prerequisites

- JDK 21 toolchain (Gradle resolves it).
- The repo checked out on `claude/constitution-followup-rwmz1f` (carries the
  constitution, `.specify/` tooling, and this feature's `engine` module once
  implemented).

## Build & test

```bash
# Full gated build: compile + detekt + Kover verification + tests (Principle III)
./gradlew build

# Just the new engine module's tests
./gradlew :engine:test
```

## Run a pipeline from YAML

Create `build-and-test.yaml`:

```yaml
pipeline:
  name: "build-and-test"
  concurrency: 2
  stages:
    - name: "build"
      steps:
        - name: "compile"
          run: "echo compiling && true"
    - name: "test"
      steps:
        - name: "unit"
          run: "echo testing && true"
```

Execute it (illustrative entry point; finalized during implementation):

```kotlin
val pipeline = PipelineDescriptor.load(Path("build-and-test.yaml"))
val run = PipelineEngine.default().run(pipeline)
println(run.status)   // Success
```

## Same pipeline via the Kotlin DSL

```kotlin
val pipeline = pipeline("build-and-test") {
    concurrency = 2
    stage("build") { step("compile") { run("echo compiling && true") } }
    stage("test")  { step("unit")    { run("echo testing && true") } }
}
val run = PipelineEngine.default().run(pipeline)
println(run.status)   // Success — identical to the YAML run (SC-002)
```

## What to verify (maps to spec success criteria)

- A two-stage pipeline of passing commands ends `Success`; a non-zero step ends the
  run `Failed` naming the step, and later steps in that stage don't run (US1).
- YAML and Kotlin definitions of the same pipeline produce identical ordering and
  status (US2 / SC-002).
- A step echoing a secret registered via `secrets:` shows the value masked in stdout —
  the raw value appears 0 times (US3 / SC-003).
- Two steps writing the same relative filename don't interfere (isolated working dirs)
  (US3 / SC-004).
- A step that sleeps past its `timeout` is killed and marked `TimedOut` within ~1s,
  with no orphaned processes (US3 / SC-005).
- With `concurrency: K`, no more than K steps are `Running` at once (SC-006).

## Notes

- v0 is stateless: runs are not persisted, logs go to stdout. Docker isolation,
  Postgres persistence, webhook triggers, and a UI arrive in v1.
- Before implementing, confirm and bump the Konstellation meta-DSL/dsl versions in
  `gradle/libs.versions.toml` and update `gradle/verification-metadata.xml`
  (research.md R1, Constitution Principle V).
```
