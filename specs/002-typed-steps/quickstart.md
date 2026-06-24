# Quickstart: Typed Step Types (Gradle / Docker / NPM)

Prereq: feature `001-pipeline-foundation` implemented (the `StepDefinition` /
`StepExecutor` seam, `StepContext`). These step types are additive on top of it.

## Build & test

```bash
./gradlew :engine:test     # includes typed-step model, builder, and executor tests
./gradlew build            # full gated build (detekt + Kover)
```

## Gradle step (YAML + DSL)

```yaml
stages:
  - name: "build"
    steps:
      - name: "compile"
        gradle: { tasks: ["build"], args: ["-x", "test"] }
```

```kotlin
stage("build") {
    gradleStep("compile") { tasks("build"); args("-x", "test") }
}
```

Both run `./gradlew build -x test` (wrapper preferred) and map the exit code to
SUCCESS/FAILED.

## Docker + NPM steps

```kotlin
dockerStep("smoke") { run { image = "node:20"; command("node", "--version") } }
npmStep("web-tests") { script("test") }   // npm run test
```

## What to verify (maps to success criteria)

- Gradle/Docker/NPM steps are expressible without raw shell strings, in YAML and DSL
  (SC-001); YAML and DSL forms produce identical models (SC-002).
- No feature-001 engine-loop code changed — only new subtypes + executors +
  registration (SC-003).
- A missing tool binary (`gradle`/`docker`/`npm`) yields a FAILED step naming the tool
  (SC-004), never an unhandled exception.
- A typed step and an equivalent `run` step show identical isolation + masking (SC-005).

## Notes

- Still in-process; `dockerStep` invokes the host `docker` CLI (tool invocation), not
  container-based runner isolation (v1).
- Real-binary executor tests are environment-gated so the suite stays green where a
  tool isn't installed.
