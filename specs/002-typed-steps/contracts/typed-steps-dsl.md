# Contract: Typed Step Kotlin DSL — feature 002

Typed builders on the Konstellation meta-DSL (`@KontinuanceDsl` + `DslBuilder<T>`,
KSP-generated — FR-008). Each produces the same `StepDefinition` subtype as the
equivalent YAML key (FR-005). Consumer-facing contract (Principle I).

```kotlin
pipeline("build-and-test") {
    stage("build") {
        gradleStep("compile") {
            tasks("build")
            args("-x", "test")
            // useWrapper defaults to true
        }
    }
    stage("package") {
        dockerStep("image") {
            build {
                context = "."
                dockerfile = "Dockerfile"
                tags("myapp:ci")
            }
        }
    }
    stage("web") {
        npmStep("web-tests") { script("test") }   // npm run test
        npmStep("deps") { installClean() }          // npm ci
    }
    stage("legacy") {
        step("echo") { run("echo hi") }             // generic run from 001 still works
    }
}
```

## Builders

- `gradleStep(name) { tasks(...); args(...); useWrapper = … }` → `GradleStep`.
- `dockerStep(name) { run { image=…; command(...) } }` or `{ build { context=…;
  dockerfile=…; tags(...) } }` → `DockerStep`.
- `npmStep(name) { script("…") }` or `{ installClean() / install() }` → `NpmStep`.
- Shared step config (`timeout`, `when`, `secrets`, `workingDir`) available on every
  typed builder, identical to the 001 `step { }`.

## Guarantees

- Each builder yields the same model as its YAML equivalent (FR-005, SC-002).
- Builders are KSP-generated through Konstellation, not hand-written (FR-008).
- Execution flows through the 001 `StepExecutor` registry + `StepContext`, so typed
  steps inherit isolation, timeout, masking, and the status lifecycle (FR-006).
