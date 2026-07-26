# Implementation Plan: Arbitrary Commit-SHA Checkout

**Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o` | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/034-commit-sha-checkout/spec.md`

## Summary

Add an optional commit **SHA** to the git checkout. `GitStep` gains a `sha` field, mutually exclusive with
`ref` (enforced in the model `init`, so the descriptor's `construct { }` turns it into a parse error). The
executor keeps `git clone` for branch/tag, and for a SHA fetches-then-checks-out via an injection-safe
`sh -c` argv (URL/SHA/dir passed as positional parameters). The descriptor gains a `sha:` key; the DSL
`gitStep { }` gains a `sha` option (Principle I parity). For a project source (033), whose single field must
serve both, a 7–40-hex value is routed to `sha` and anything else to `ref` (`ProjectSourceInjector`). A
checkout with no SHA is unchanged.

## Technical Context

**Language/Version**: Kotlin/JDK 21 engine (`:engine`) + Spring WebFlux (`:server`) + Svelte 5 (`web`).

**Primary Dependencies**: none new — uses `git` + `sh` on the runner (already required by the clone path and
`RunStep`).

**Storage**: none — the project source sidecar (033) already carries the value; SHA vs branch is a routing
decision, not new state.

**Testing**: `GitStepExecutorTest` — `shaArgv` purity + a real fetch-by-SHA checkout against a seeded local
repo (two commits, `uploadpack.allowReachableSHA1InWant`, checkout the first commit), guarded by git-on-PATH;
descriptor test — `sha:` parses and `ref`+`sha` together is rejected; `GitStepDslTest` — `sha` option;
`ProjectSourceInjectorTest` — a SHA value sets `sha` (clearing `ref`), a name sets `ref`.

**Constraints**: `sha`/`ref` mutually exclusive; no-SHA path unchanged; injection-safe argv (positional
params); no new dependency; no silent fallback on an unfetchable SHA.

**Scale/Scope**: `engine/model/GitStep.kt` (+`sha`), `engine/execution/steps/GitStepExecutor.kt` (SHA argv),
`engine/descriptor/PipelineDescriptor.kt` (`sha` key), `engine/dsl/steps/GitStepBuilder.kt` (+`sha`);
`server/.../trigger/ProjectSourceInjector.kt` (SHA routing); web `components/Login.svelte` (field hint);
tests + docs.

## Constitution Check

- **I. Platform-First & Stable Public Contract**: PASS — additive `sha` key on the `git:` step and DSL (same
  model both produce); the no-SHA clone path and every other contract are unchanged.
- **II. Test-First & Integration-Verified**: PASS — the SHA argv is unit-tested and the checkout is
  integration-tested against a real local repo; parse rejection and injector routing are unit-tested.
- **III. Quality Gates**: PASS — detekt/Kover on `:engine` and `:server`; svelte-check + Vitest + Playwright
  on `web`.
- **IV. Code Generation**: N/A — `GitStep` is a hand-written model with a hand-written DSL builder (not
  `@GeneratedDsl`); both are updated in lockstep.
- **V. Supply-Chain Integrity**: PASS — no new dependency; `gradle/verification-metadata.xml` untouched.

No violations → Complexity Tracking empty.

## Project Structure

```text
engine/.../model/GitStep.kt                         # EDIT — + sha field; ref/sha mutually exclusive
engine/.../execution/steps/GitStepExecutor.kt        # EDIT — SHA path: injection-safe sh -c fetch+checkout
engine/.../descriptor/PipelineDescriptor.kt          # EDIT — GIT_KEYS + "sha"; parseGit reads sha
engine/.../dsl/steps/GitStepBuilder.kt               # EDIT — + sha option
engine/.../execution/steps/GitStepExecutorTest.kt (test)  # EDIT — shaArgv purity + real SHA checkout
engine/.../descriptor/TypedStepDescriptorTest.kt (test)   # EDIT — sha parses; ref+sha rejected
engine/.../dsl/steps/GitStepDslTest.kt (test)             # EDIT — sha option
server/.../trigger/ProjectSourceInjector.kt          # EDIT — route a SHA value to sha, else ref
server/.../trigger/ProjectSourceInjectorTest.kt (test)    # EDIT — SHA vs branch routing
web/src/lib/components/Login.svelte                  # EDIT — "branch, tag, or commit SHA" field hint
docs/getting-started.md + docs/roadmap.md            # EDIT — commit-SHA checkout (034)
```

**Structure Decision**: Realize the SHA in the executor (fetch + `checkout FETCH_HEAD`) rather than adding a
new step type, so masking/status/isolation are inherited unchanged and only the argv differs. Keep the
descriptor's `ref`/`sha` as **distinct keys** (no guessing there); apply the hex heuristic only at the project
source boundary, where a single UI field must express either. Enforce mutual exclusion in the model so both
front-ends (descriptor + DSL) reject "both set" through one code path.

## Complexity Tracking

> No Constitution Check violations — no entries.
