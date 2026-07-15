# Run Kontinuance as external CI for GitHub

The `kontinuance-ci` service polls GitHub (outbound only — no inbound exposure), runs a repository's
pipeline for new PR commits (and, optionally, for pushes to a tracked branch), and reports the outcome
back to GitHub as a **commit status** on the head SHA under the stable context `kontinuance/ci`. Make
that check **required** in branch protection and a PR can't merge until Kontinuance reports success.

## 1. Install the service launcher

```bash
./gradlew :github:install     # installs `kontinuance-ci` to ~/.local/bin (ensure it's on PATH)
```

## 2. Provide a token

Create a GitHub PAT with `repo` (or at least `repo:status`) scope and export it under the name your
config's `tokenEnv` points at:

```bash
export GITHUB_TOKEN=ghp_xxx
```

The token is read from the environment and never written to the config or the logs.

## 3. Write a config

See [`kontinuance-ci.yaml`](./kontinuance-ci.yaml) — native Kontinuance schema, not a GitHub Actions
workflow. Each repository names a `prPipeline` (the gating check) and optionally a `pushPipeline`
(delivery on a push to `trackedBranch`). Relative pipeline paths resolve against the config file's
directory. The referenced pipelines are ordinary [Kontinuance descriptors](../../docs/cli.md) — e.g.
your [publish pipeline](../publish-artifacts/) as the `pushPipeline`.

## 4. Run it

```bash
kontinuance-ci kontinuance-ci.yaml
# → kontinuance-ci: watching 1 repo(s), polling every 30s
```

On each poll it posts `pending` on a new head SHA, runs the pipeline through the engine, then posts
`success`/`failure`. The commit's SHA is injected into the run as the `KONTINUANCE_SHA` secret, so a
delivery pipeline publishes exactly the observed commit. A durable cursor
(`~/.kontinuance/github-cursors.properties`) means a restart resumes without re-running seen commits.

## What's implemented

- **PR checks** and **push-to-tracked-branch delivery** (poll-based), commit-status reporting with the
  stable `kontinuance/ci` context, `KONTINUANCE_SHA` injection, retry/backoff on transient GitHub
  errors, and a durable poll cursor.
- **Manual (re-)trigger** is available programmatically (`EventSource.triggerManual`); a CLI subcommand
  for it, plus the optional signature-verified webhook mode, are noted as follow-ups.
