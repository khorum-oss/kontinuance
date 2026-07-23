# Tasks: Sandbox Demo — Build & Test a Real App

**Feature**: 020-sandbox-demo | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Input**: [plan.md](./plan.md), [spec.md](./spec.md)

Everything lives under `sandbox/` (a target app, **not** a Kontinuance module). No engine/web change, no new
dependency. `[P]` = parallelizable.

## Phase 1: The example application

- [ ] T001 [P] `sandbox/settings.gradle.kts` (`rootProject.name = "kontinuance-sandbox"`) — standalone,
  absent from the root `settings.gradle.kts`.
- [ ] T002 [P] `sandbox/build.gradle.kts` — `application` plugin, JDK 21 toolchain, a `selfTest` `JavaExec`
  task wired into `check`; no repositories/dependencies (offline).
- [ ] T003 [P] `sandbox/src/main/java/com/example/sandbox/{Calculator,App,SelfTest}.java` — trivial logic,
  a `run` entry point, and a zero-dependency assertion runner (ASCII output; `System.exit(1)` on failure).
- [ ] T004 [P] `sandbox/.gitignore` — `build/`, `.gradle/`.

## Phase 2: The pipeline + fresh local run (US1)

- [ ] T005 [US1] `sandbox/kontinuance.yml` — `checkout` (`run: git clone "$REPO_URL" app`, `secrets:
  [REPO_URL]`) → `build` (`gradle: assemble`, `workingDir: app`) → `test` (`gradle: selfTest`,
  `workingDir: app`).
- [ ] T006 [US1] `sandbox/run.sh` — snapshot the app into a throwaway local git repo, set `REPO_URL`, build
  the `kontinuance` CLI if absent, and run the descriptor through it (streaming logs). Executable.
- [ ] T007 [US1] Verify end to end: `sandbox/run.sh` → checkout + `gradle assemble` + `selfTest` all run,
  `pipeline 'sandbox-ci' finished: Success`, exit 0; a second run starts clean.

## Phase 3: Red path (US2) + UI path (US3)

- [ ] T008 [US2] Confirm the failing path: `FAIL_SANDBOX=true gradle -p sandbox selfTest` exits non-zero
  (and breaking `Calculator` makes the pipeline's test step Fail). Document it in the README.
- [ ] T009 [US3] `sandbox/README.md` — what it is; run it fresh (`run.sh`); run it through the server/UI
  (point `KONTINUANCE_CONFIG_DESCRIPTOR` at the descriptor + a reachable `REPO_URL`); the red path.

## Phase 4: Docs

- [ ] T010 Point `docs/getting-started.md` at the sandbox as the **real runnable example** (distinct from the
  `echo`-only demo), so the "how do I see it build real code" question is answered.

## Dependencies & MVP

- Phase 1 → Phase 2. **MVP = US1** (a fresh local run that builds + tests real code). US2/US3 add the red
  path and the UI path; both reuse the same app + descriptor.
