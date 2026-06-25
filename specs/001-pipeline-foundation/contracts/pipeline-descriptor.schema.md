# Contract: Pipeline Descriptor (YAML) — v0

The declarative front-end of the hybrid (Option C) pipeline definition. A descriptor
parses into the exact pipeline model in `data-model.md`. This is a **consumer-facing
contract** (Constitution Principle I): breaking it requires a MAJOR version change.

## Shape

```yaml
pipeline:
  name: "build-and-test"          # required, non-empty
  concurrency: 2                  # optional, integer ≥ 1; default 1
  stages:                         # required (may be empty)
    - name: "build"               # required, unique within pipeline
      steps:                      # required (may be empty)
        - name: "compile"         # required, unique within stage
          run: "./gradlew build"  # required: shell command
          timeout: "5m"           # optional duration (>0); default platform value
          when: true              # optional condition; false ⇒ step SKIPPED
          secrets: ["TOKEN"]      # optional list of secret names to inject + mask
          workingDir: "."         # optional relative subdir within the isolated dir
    - name: "test"
      steps:
        - name: "unit"
          run: "./gradlew test"
```

## Rules

1. `pipeline.name`, `stage.name`, `step.name` are required and non-empty; names are
   unique within their parent scope.
2. `run` is required for every step.
3. Unknown keys, missing required keys, or malformed values ⇒ a parse/validation error
   identifying the location; **no step executes** (FR-003).
4. `concurrency` ≥ 1; `timeout` parses to a positive `Duration`.
5. `secrets` entries are resolved through `SecretSource` and masked in logs; an
   unresolved secret name ⇒ validation error (fail fast) rather than leaking an empty
   value.
6. `workingDir` MUST resolve inside the step's isolated working directory.

## Outcome mapping

- All steps exit `0` ⇒ run status `Success`.
- A step exits non-zero ⇒ that step `Failed(step, reason)`; its stage stops and the
  run is `Failed` (FR-005).
- A step exceeds `timeout` ⇒ `TimedOut`.
- `when` false ⇒ step `Skipped`, stage continues (FR-015).

## Equivalence guarantee

A descriptor and its Kotlin-DSL equivalent (see `dsl-and-engine-api.md`) MUST produce
the same pipeline model and therefore the same execution ordering and final status
(FR-002, SC-002).
