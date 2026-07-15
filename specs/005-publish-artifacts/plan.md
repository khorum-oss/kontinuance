# Implementation Plan: Publish-Artifacts Enablement

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` (005 numbers the specs dir only) | **Date**: 2026-07-15 | **Spec**: [spec.md](./spec.md)

## Summary

Ship a native Kontinuance pipeline **example** + **quickstart** that publishes Maven artifacts to a
configurable repository from the installed `kontinuance` CLI, driven entirely by the existing engine
(no engine change). Repository URL + credentials arrive as masked `secrets:` (resolved from env by
`EnvSecretSource`); the descriptor is authored in Kontinuance's own schema and carries no GitHub-YAML
provenance. Verified end-to-end against a local `file://` Maven repository.

## Technical Context

**Engine facts that shape the example** (from `execution/StepRunner.kt` + `ProcessStepExecutor.kt`):
- Each step runs in a **fresh isolated temp dir**; project files are NOT there → commands must
  `cd "$PROJECT_DIR"` (an absolute path) to operate on a real project.
- The step environment is **cleared** except passthrough `PATH/HOME/LANG/LC_ALL/TMPDIR`; the only way
  to inject `PROJECT_DIR`, `PUBLISH_REPO_URL`, and credentials is via `secrets:` (env-backed, masked).
- `run` executes through a shell, so `cd`, `&&`, and `$VAR` expansion are available.

**Deliverables**: `examples/publish-artifacts/{publish-artifacts.yaml, README.md, sample-lib/…}` — a
native descriptor, a quickstart, and a minimal standalone `sample-lib` (core `java-library` +
`maven-publish` only, so it builds/publishes offline with no external plugins) used to prove the path.

**Verification**: run the descriptor via the `kontinuance` CLI with `PUBLISH_REPO_URL=file://…` and
confirm the JAR + POM + checksums land in that repo; run with a missing credential to confirm
fail-fast + no upload; grep run output to confirm secrets are masked.

**Out of scope**: 003 GitHub trigger; Web UI; any engine/public-contract change (FR-010).

## Constitution Check

- **I. Stable Public Contract** — PASS. No engine/DSL/API change; example + docs only.
- **II. Test-First & Integration-Verified** — PASS. The publish path is exercised against the real
  `file://` boundary end-to-end (the v0 analogue of a real integration test).
- **III. Quality Gates** — PASS. `examples/` is not a source module; the existing gates are untouched
  and stay green. `sample-lib` is a standalone build, excluded from the aggregate.
- **IV / V** — N/A (no codegen; no new dependency in the engine build; no secrets committed — the
  example reads them from env).

**Result: PASS.**

## Project Structure

```text
examples/publish-artifacts/
├── README.md                 # quickstart: secrets, run, repo-kind notes (Nexus/Artifactory/GH Packages/S3)
├── publish-artifacts.yaml    # native Kontinuance descriptor: build stage + publish stage
└── sample-lib/               # minimal standalone gradle build to demo/verify (java-library + maven-publish)
    ├── settings.gradle.kts
    ├── build.gradle.kts       # publishes to $PUBLISH_REPO_URL with optional $PUBLISH_REPO_USER/PASSWORD
    └── src/main/java/com/example/kontinuance/sample/Greeting.java
```

**Structure Decision**: everything lives under a new top-level `examples/` directory; nothing touches
the engine modules or the root build. `sample-lib` is its own Gradle build (own settings), so it is
not part of the kontinuance multi-module build or its quality aggregate.
