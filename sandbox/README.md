# Sandbox — a real app for Kontinuance to build & test

This is a **tiny, self-contained Gradle application** that exists so you can watch Kontinuance actually
check out real code and run its build + tests — end to end, fresh, from a local run. It's the concrete
counterpart to the shipped `echo`-only demo (which only exercises the engine/approval flow).

- [`src/…/Calculator.java`](src/main/java/com/example/sandbox/Calculator.java) — trivial logic to build.
- [`src/…/SelfTest.java`](src/main/java/com/example/sandbox/SelfTest.java) — a **zero-dependency** stand-in
  for a test suite: it asserts the calculator's behaviour and exits non-zero on failure, so a real
  regression turns the pipeline red. No JUnit, no downloads — the whole run is offline and deterministic.
- [`kontinuance.yml`](kontinuance.yml) — the pipeline: **checkout → assemble → self-test**.
- [`run.sh`](run.sh) — one command to run it all fresh.

It is intentionally **not** part of the Kontinuance build (absent from the root `settings.gradle.kts`) —
Kontinuance treats it exactly like any external application repo.

## Prerequisites

- **JDK 21** and **Gradle on your PATH.** The app ships no Gradle wrapper on purpose, so the pipeline's
  `gradle:` step exercises its **system-`gradle` fallback**. (`git` is also needed for the checkout.)

## Run it fresh (one command)

```bash
sandbox/run.sh
```

What happens: `run.sh` snapshots this app into a throwaway local git repo, builds the `kontinuance` engine
CLI (first time only), and runs the pipeline through it. The engine checks the app out into a **fresh,
ephemeral workspace**, runs `gradle assemble` then the `selfTest`, streams the real step logs, and removes
the workspace. Every run starts from a clean checkout — nothing persists.

Expected tail:

```
[clone] Cloning into 'app'...
[assemble] BUILD SUCCESSFUL in 18s
[self-test] running CalculatorTest
[self-test]   [PASS] add returns the sum
[self-test]   [PASS] subtract returns the difference
[self-test]   [PASS] multiply returns the product
[self-test] 3 passed, 0 failed
[self-test] BUILD SUCCESSFUL in 8s
pipeline 'sandbox-ci' finished: Success
```

Point it at **your own** repo instead of the bundled app by setting `REPO_URL` and running the descriptor
through the CLI directly:

```bash
REPO_URL=https://github.com/you/your-app \
  engine/build/install/kontinuance/bin/kontinuance sandbox/kontinuance.yml
```

## Run it through the web UI

Point the server at this descriptor, then trigger it from the dashboard and watch the **real step logs**
(feature 018) appear in the run detail:

```bash
REPO_URL="https://github.com/you/your-app" \
KONTINUANCE_CONFIG_DESCRIPTOR="$(pwd)/sandbox/kontinuance.yml" \
KONTINUANCE_STORE="$(pwd)/.local/runs" \
  ./gradlew :server:run
```

`REPO_URL` must be a repo the server can clone — your GitHub URL, or a local `git` repo of this app (see
`run.sh` for how it snapshots one into `file://…`). Then open the UI, click **RUN PIPELINE**, open the run,
and watch `checkout → build → test` stream. See [docs/getting-started.md](../docs/getting-started.md) for
the full UI walkthrough.

## See it go red

Break a check to prove the pipeline fails on a real regression — either edit `Calculator.java` to return a
wrong value, or force it directly:

```bash
FAIL_SANDBOX=true gradle -p sandbox selfTest    # → BUILD FAILED, exit 1
```

Run through Kontinuance after breaking `Calculator`, and the `test` step (and the run) go **Failed**, naming
the step.
