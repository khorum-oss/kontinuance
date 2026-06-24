# Contract: Typed Step Descriptor Keys (YAML) — feature 002

Extends the 001 descriptor (`contracts/pipeline-descriptor.schema.md`). A step object
declares **exactly one** of `run:` / `gradle:` / `docker:` / `npm:`. Consumer-facing
contract (Constitution Principle I).

```yaml
steps:
  - name: "compile"            # gradle step
    gradle:
      tasks: ["build"]
      args: ["-x", "test"]
      useWrapper: true         # optional, default true
  - name: "image"              # docker build
    docker:
      build:
        context: "."
        dockerfile: "Dockerfile"
        tags: ["myapp:ci"]
  - name: "smoke"              # docker run
    docker:
      run:
        image: "node:20"
        command: ["node", "--version"]
  - name: "web-tests"          # npm step
    npm:
      script: "test"           # → npm run test
  - name: "deps"
    npm:
      install: { clean: true } # → npm ci
  - name: "legacy"             # generic shell still supported
    run: "echo hi"
```

## Rules

1. Exactly one of `run`/`gradle`/`docker`/`npm` per step; zero or more than one ⇒
   validation error identifying the step (no execution).
2. `gradle.tasks` non-empty; `docker` has exactly one of `run`/`build`;
   `npm` has exactly one of `script`/`install`.
3. Shared step fields from 001 (`timeout`, `when`, `secrets`, `workingDir`) apply to
   typed steps unchanged.
4. Each typed key maps to its `StepDefinition` subtype (see `data-model.md`) and
   therefore the same model the Kotlin DSL produces (FR-005).

## Outcome mapping

Identical to 001: the tool's exit `0` ⇒ step SUCCESS, non-zero ⇒ FAILED (code
surfaced); timeout ⇒ TIMED_OUT; `when` false ⇒ SKIPPED. A missing tool binary ⇒
FAILED naming the tool (FR-007).
