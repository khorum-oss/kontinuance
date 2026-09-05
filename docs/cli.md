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

A self-contained, **read-only** pipeline that exercises the engine end to end — good for confirming
the CLI works after an install or change. It touches nothing outside its own shell. Save as
`smoke.yaml`:

```yaml
pipeline:
  name: "cli-smoke"
  concurrency: 1
  stages:
    - name: "verify"
      steps:
        - name: "shell-available"
          run: "echo kontinuance-smoke-ok"
        - name: "workspace-writable"
          run: "touch smoke.marker && test -f smoke.marker"
```

Then:

```bash
kontinuance --check smoke.yaml   # expect: "descriptor OK: 'cli-smoke' — 1 stage(s)" + the two steps
kontinuance smoke.yaml           # expect: "pipeline 'cli-smoke' finished: Success"
```

What this proves: the full path works — descriptor load → `PipelineEngine.default()` → `RunStep`
executed via `ProcessBuilder` → status mapped to an exit code.

To smoke-test against a real deployment instead, swap the steps for read-only `curl`s at a health or
readiness endpoint you control.

> Keep your reusable **delivery** descriptors (build → push → deploy → UAT, and any prod promotion)
> wherever your deployment configuration lives. Validate them anytime with
> `kontinuance --check <path>` before a real (heavier) run.

## Notes

- The runner currently lives in the `engine` module (`org.khorum.oss.kontinuance.engine.cli.Runner`).
  A dedicated `cmd` module is a possible later refactor.
- `--check` is what an editor integration or the `003` event source can call to validate a repo's
  pipeline before scheduling a run.
