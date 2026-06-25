# Phase 0 Research: Pipeline Execution Foundation (v0)

Resolves the unknowns flagged in the Technical Context. Each item records the
decision, rationale, and alternatives considered.

## R1 — Konstellation meta-DSL version

- **Status**: RESOLVED & APPLIED.
- **Decision**: Pinned to the newest releases on the Khorum Reliquary repo:
  `konstellation-meta-dsl = 1.0.15` and `konstellation-dsl = 2.0.14`.
- **Note (breaking)**: the DSL artifact was **renamed** in 2.x — coordinate changed
  from `org.khorum.oss.konstellation:dsl` to
  `org.khorum.oss.konstellation:konstellation-dsl`; `gradle/libs.versions.toml`
  updates both the version and the module coordinate.
- **Rationale**: User explicitly requested the most recent Konstellation DSL. The
  hybrid DSL (Option C) is built on the meta-DSL, so it tracks the latest.
- **Verification**: `org.khorum.*` is trusted by a group regex rule in
  `gradle/verification-metadata.xml`, so no per-artifact checksums are needed;
  `./gradlew --write-verification-metadata sha256,pgp build` added the one new
  transitive trusted-key (`checker-qual`) and the full build (detekt + Kover) passed
  with the major `dsl` bump — the existing `dsl` module still compiles.

## R2 — Pipeline DSL approach (confirmed)

- **Decision**: **Option C (hybrid)** — a declarative YAML/TOML descriptor for simple
  pipelines plus a Kotlin DSL escape hatch for complex ones, both producing one
  pipeline model consumed by a single engine.
- **Rationale**: Matches the overview's leaning and the user's choice; YAML lowers
  adoption cost, the Kotlin DSL covers expressiveness, and a shared model keeps the
  engine front-end-agnostic.
- **Alternatives**: Option A (Kotlin-only) — less adoptable for non-Kotlin teams;
  Option B (YAML-only) — loses Kotlin expressiveness and the Konstellation synergy.

## R3 — Descriptor format & parser

- **Decision**: Support a YAML descriptor for v0. Parse into the pipeline model via a
  small, verification-friendly library; prefer `kotlinx-serialization` with a YAML
  format if a vetted one is acceptable, otherwise a focused YAML parser
  (e.g. SnakeYAML/kaml) added through the version catalog.
- **Rationale**: `kotlinx-serialization-json` is already in the catalog; reusing the
  serialization model keeps one mapping path. Final library pick is made at
  implementation time to satisfy dependency verification cleanly.
- **Alternatives**: Hand-rolled parser — rejected (error-prone, reinvents parsing);
  TOML-only — YAML is the more familiar CI descriptor format. (TOML may be added later
  without changing the model.)

## R4 — Execution & isolation model

- **Decision**: In-process engine using Kotlin structured concurrency
  (`coroutineScope`/`supervisorScope`), launching each step's command with
  `ProcessBuilder`. Each step gets a freshly created temp working directory and an
  explicitly constructed environment map (not the inherited process environment),
  removed on terminal status. Per-step timeout via `withTimeout`, terminating the
  process tree (`Process.destroyForcibly()` / `descendants()`).
- **Rationale**: Matches the overview's v0 ("embedded coroutine executor" /
  `ProcessBuilder`); gives real isolation without container ops overhead and exercises
  the real OS boundary for tests (Principle II).
- **Alternatives**: Docker/k8s — deferred to v1+; thread pools without coroutines —
  more callback complexity, no structured cancellation.

## R5 — Concurrency control

- **Decision**: A `kotlinx.coroutines.sync.Semaphore` with permits = configured
  concurrency cap K gates step launches; runnable steps suspend until a permit frees.
- **Rationale**: Idiomatic, structured, satisfies SC-006 (never exceed K) without
  busy-waiting.
- **Alternatives**: Fixed thread pool sizing — coarser and harder to coordinate with
  coroutine cancellation.

## R6 — Status model

- **Decision**: A `sealed` `PipelineStatus` (and per-step status) hierarchy covering
  Pending, Queued, Running, Success, Failed(step, reason), Cancelled, TimedOut,
  Skipped, WaitingOnApproval; transitions emitted to observers via a coroutine
  `Flow`/`SharedFlow`.
- **Rationale**: Exhaustive `when` handling, explicit illegal-state avoidance (overview
  guidance), and reactive observation without coupling to a transport.
- **Alternatives**: enum — cannot carry per-state data (e.g. failing step + reason);
  Spring State Machine — heavier than needed for v0.

## R7 — Logging & secret masking

- **Decision**: Per-step stdout/stderr captured and streamed to process stdout as an
  append-only line stream, passed through a masking filter that replaces any
  registered secret substring before emission. Secrets resolved via a `SecretSource`
  abstraction (v0 implementation reads environment variables).
- **Rationale**: Meets FR-010/011/012 and SC-003; keeps storage/transport pluggable
  (SSE/WebSocket + persistent store arrive in v1) and keeps logs out of any datastore
  per the overview.
- **Alternatives**: Logging straight through a datastore — explicitly discouraged by
  the overview; masking at sink only — riskier than masking at the single emission
  point.

## R8 — Spring Boot footprint in v0

- **Decision**: Keep the `engine` core framework-agnostic (plain Kotlin + coroutines)
  so it is unit/integration testable without a Spring context. Spring Boot wiring (the
  eventual orchestrator app/API) is introduced minimally or deferred to v1; if any
  Spring code lands in v0 it is thin and additive.
- **Rationale**: The constitution names Spring Boot as the platform runtime, but v0's
  value (the execution engine) does not need a web context; keeping it decoupled keeps
  tests fast and the contract clean.
- **Alternatives**: Build the engine inside a Spring app from day one — adds context
  startup cost to every test for no v0 benefit.

## Open items carried to implementation

- ~~Confirm and pin the newest Konstellation meta-DSL/dsl versions (R1).~~ DONE:
  meta-dsl 1.0.15, dsl 2.0.14 (artifactId renamed to `konstellation-dsl`).
- Final YAML library selection compatible with dependency verification (R3).
