# Contract: Checkout Step & Workspace

## Workspace

- One directory per run, created at run start, removed at run end (any terminal/paused status).
- Every step's working directory is the workspace (or a `workingDir:` sub-path resolved **inside** it).
- Isolation: cannot escape to the host; removed on run end. Secret masking + env scoping stay per step.

## `GitStep` model

| Field | YAML key | DSL | Default | Meaning |
|---|---|---|---|---|
| url | `url` | `url` | (required) | Repository URL to clone |
| ref | `ref` | `ref` | none | Branch or tag to clone (`--branch`) |
| dir | `dir` | `dir` | `"."` | Target sub-path in the workspace |
| depth | `depth` | `depth` | `1` | Shallow clone depth (`--depth`) |

## YAML (strict parser)

```yaml
- name: "checkout"
  git:
    url: "https://github.com/you/your-repo"
    ref: "main"     # optional (branch/tag)
    dir: "."        # optional
    depth: 1        # optional
```

`git` is one of the step-type keys `{run, gradle, docker, npm, approval, git}` — exactly one per step.

## DSL

```kotlin
gitStep("checkout") {
    url = "https://github.com/you/your-repo"
    ref = "main"
}
```

Produces the identical `GitStep` model as the YAML (Principle I).

## Execution

`git clone [--depth <depth>] [--branch <ref>] <url> <dir>`, run in the workspace via the shared
`ProcessStepExecutor` (single argv — no shell). A missing `git` or unreachable repo → a FAILED step
naming it. Branch/tag refs only (arbitrary SHA is a follow-up). Cloning into `.` requires an empty target
(put the checkout first, or use a sub-`dir`).
