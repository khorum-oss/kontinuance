# Implementation Plan: Sandbox Demo — Build & Test a Real App

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-sandbox-demo/spec.md`

## Summary

Add a `sandbox/` directory holding a **standalone, zero-dependency Gradle application** (a `Calculator` + a
plain-Java `SelfTest` that asserts and exits non-zero on failure) plus a Kontinuance pipeline
(`checkout → assemble → self-test`) and a `run.sh` that drives a fresh, local, end-to-end run through the
existing `kontinuance` engine CLI. `run.sh` snapshots the app into a throwaway local git repo and passes it
as `REPO_URL`; the pipeline's checkout step clones it into the run's fresh workspace, and the typed `gradle:`
steps build and test it there (system `gradle`, since the app ships no wrapper — demonstrating that
fallback). No Kontinuance code changes, no new dependency; the example is deliberately **not** in the root
`settings.gradle.kts`, so it never touches Kontinuance's own build or gates.

## Technical Context

**Language/Version**: the example is Java on the JDK 21 toolchain, built with Gradle. Kontinuance itself is
unchanged.

**Primary Dependencies**: none — the example uses only core Gradle plugins (`application`) and no libraries;
the demo reuses the existing engine CLI (`Runner`) and the `git`/`gradle` step types (015/002).

**Storage**: none new (the engine's per-run temp workspace, removed at run end).

**Testing**: verified by running the pipeline end to end via the CLI (checkout → build → self-test → Success,
exit 0) and the failing path (`FAIL_SANDBOX=true` → the test step fails, non-zero); the example's own
`gradle assemble`/`selfTest` run offline.

**Target Platform**: any host with JDK 21, Gradle, and `git` on the PATH.

**Constraints**: offline/deterministic (no external deps); the example must not join Kontinuance's build; no
engine change; `run.sh` must work from a clean checkout and stream the real logs.

**Scale/Scope**: ~6 files under `sandbox/` + a docs pointer. No `.kt`/`web` change.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — no engine/DSL/API change; the example consumes the
  existing `git`/`gradle` step types and the CLI. Purely additive.
- **II. Test-First & Integration-Verified**: PASS — the demo *is* an integration exercise of the real engine
  (checkout + build + test over the actual process boundary); both green and red paths are verified.
- **III. Quality Gates**: PASS — the example is outside the Kontinuance build, so detekt/Kover/Sonar are
  unaffected; Kontinuance's gates stay green.
- **IV. Code Generation**: N/A.
- **V. Supply-Chain Integrity**: PASS — no new Kontinuance dependency; `verification-metadata.xml` untouched.
  The example has no dependencies to verify.

No violations → Complexity Tracking empty.

## Project Structure

```text
sandbox/
├── settings.gradle.kts                         # standalone project (NOT in the root settings)
├── build.gradle.kts                            # application plugin + a `selfTest` JavaExec, JDK 21, no deps
├── src/main/java/com/example/sandbox/
│   ├── Calculator.java                         # the logic to build
│   ├── App.java                                # `gradle run` entry point
│   └── SelfTest.java                           # zero-dep assertions; System.exit(1) on failure
├── kontinuance.yml                             # checkout (REPO_URL secret) → assemble → self-test
├── run.sh                                       # snapshot app → local git repo → run pipeline via the CLI
├── README.md                                   # what it is + how to run (local, UI, red path)
└── .gitignore                                  # build/ .gradle/

docs/getting-started.md                         # EDIT — point at the sandbox as the real runnable example
```

**Structure Decision**: A single `sandbox/` target app, kept out of the platform build, driven by a script
over the existing engine CLI. The pipeline uses a `run:` clone (parameterised by `REPO_URL`) plus typed
`gradle:` steps — showcasing both step kinds and the real checkout → build → test flow on an ephemeral
workspace, with nothing new added to Kontinuance itself.

## Complexity Tracking

> No Constitution Check violations — no entries.
