# Kontinuance CLI

Run a pipeline descriptor through the engine from the command line.

## Install

```bash
./gradlew :engine:install
```

Installs the `kontinuance` command to `~/.local/bin` (the app distribution lives under
`~/.local/lib/kontinuance`). Make sure `~/.local/bin` is on your `PATH`:

```bash
export PATH="$HOME/.local/bin:$PATH"   # add to ~/.zshrc / ~/.bashrc if not already
```

No install needed for a one-off: `./gradlew :engine:run --args="<descriptor.yaml>"`.

## Use

```bash
kontinuance <pipeline-descriptor.yaml>          # load → run in-process → print outcome
kontinuance --check <pipeline-descriptor.yaml>  # parse + print structure, run NOTHING
```

**Exit codes:** `0` success · `1` the pipeline finished in a failure state · `2` usage / a
missing, unreadable, or malformed descriptor.

`--check` is a safe validation pass — it loads the descriptor and prints each stage/step (with
step type, `secrets`, `workingDir`) without executing anything. Use it to catch descriptor errors
before a real run.

## Verify the CLI (smoke test)

A self-contained, **read-only** pipeline that exercises the engine against the live Relikquary
stage registry — good for confirming the CLI works end-to-end after an install or change. Save as
`relikquary-uat.yaml`:

```yaml
pipeline:
  name: "relikquary-uat-smoke"
  concurrency: 1
  stages:
    - name: "uat"
      steps:
        - name: "smoke-registry"
          run: "curl -sfk https://stage.192.168.50.206.nip.io:30443/v2/ >/dev/null"
        - name: "smoke-readiness"
          run: "curl -sfk https://stage.192.168.50.206.nip.io:30443/actuator/health/readiness | grep -q UP"
```

Then:

```bash
kontinuance --check relikquary-uat.yaml   # expect: "descriptor OK: 'relikquary-uat-smoke' — 1 stage(s)" + the two steps
kontinuance relikquary-uat.yaml           # expect: "pipeline 'relikquary-uat-smoke' finished: Success"
```

What this proves: the full path works — descriptor load → `PipelineEngine.default()` →
`RunStep` executed via `ProcessBuilder` (the two `curl`s) → status mapped to an exit code. It hits
live infra but changes nothing (anonymous reads; no build, push, or deploy).

> The reusable Relikquary **delivery** descriptors (build → push → render → sync → UAT, and the
> prod promotion) live in `hestia-systems/platform/deploy/pipelines/`. Validate them anytime with
> `kontinuance --check <path>` before a real (heavier) run.

## Notes

- The runner currently lives in the `engine` module (`org.khorum.oss.kontinuance.engine.cli.Runner`).
  A dedicated `cmd` module is a possible later refactor.
- `--check` is what an editor integration or the `003` event source can call to validate a repo's
  pipeline before scheduling a run.
