# Tasks: Arbitrary Commit-SHA Checkout

**Feature**: 034-commit-sha-checkout | **Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## Engine

- [ ] T001 `GitStep`: add `sha: String? = null`; `init` rejects `ref` + `sha` together and a blank `sha`;
  update KDoc (a SHA is fetched-then-checked-out).
- [ ] T002 `GitStepExecutor`: `command()` returns the clone argv when `sha` is null, else `shaArgv` — an
  injection-safe `sh -c` that inits the dir, fetches the commit (honoring `depth`), and checks out
  `FETCH_HEAD`, with url/sha/dir passed as positional parameters.
- [ ] T003 `PipelineDescriptor`: `GIT_KEYS` += `sha`; `parseGit` reads `sha` (mutual exclusion enforced by the
  model via `construct { }`).
- [ ] T004 `GitStepBuilder`: add a `sha` option, passed through to the model.
- [ ] T005 `GitStepExecutorTest`: `shaArgv` purity; a real fetch-by-SHA checkout against a seeded two-commit
  local repo (checkout the first commit), guarded by git-on-PATH.
- [ ] T006 `TypedStepDescriptorTest`: a `git:` step with `sha` parses; `ref` + `sha` together is rejected.
- [ ] T007 `GitStepDslTest`: `gitStep { sha = … }` yields the same model.

## Server

- [ ] T008 `ProjectSourceInjector`: route a source value that is a commit SHA (hex, 7–40) to `GitStep.sha`
  (clearing `ref`), any other value to `ref` (clearing `sha`) — in both the override and synthesize paths.
- [ ] T009 `ProjectSourceInjectorTest`: a SHA value pins `sha`; a branch name sets `ref`; overriding a
  descriptor `git:` step swaps cleanly between the two.

## Web

- [ ] T010 `Login.svelte`: the source/branch inputs read "branch, tag, or commit SHA" (add panel + card
  source editor); no behavior change.

## Docs

- [ ] T011 `docs/getting-started.md` + `docs/roadmap.md`: commit-SHA checkout (034) closes the 015/033 caveat.

## Verification

- [ ] T012 `:engine:test :engine:detekt :server:test :server:detekt :server:koverVerify -Pdependency.env=public`
  green; web `svelte-check`, Vitest, Playwright green.
