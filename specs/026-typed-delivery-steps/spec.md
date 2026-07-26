# Feature Specification: Typed Delivery-Step Wrappers (render/deploy/uat)

**Feature Branch**: `claude/kontinuance-cross-app-alignment-w3hk0o`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User picked **Typed-step wrappers** (roadmap item 7): `render`→**zosn**, `deploy`→**logos**,
`UAT`→**euri** (Playwright), each a first-class step. Today a pipeline invokes these khorum delivery tools
through a raw `run:` shell line; this gives them typed steps — `render:`/`deploy:`/`uat:` in the descriptor
and `renderStep`/`deployStep`/`uatStep` in the Kotlin DSL — that run the tool with the shared step envelope
(secrets, workingDir, timeout, and 024 container isolation), just like `gradleStep`/`dockerStep`/`npmStep`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Declare a delivery step by name (Priority: P1)

A pipeline author writes `render:`, `deploy:`, or `uat:` (or the DSL equivalent) instead of a raw shell
command, and Kontinuance runs the right khorum tool (`zosn`/`logos`/`euri`) with the given arguments.

**Why this priority**: These are the named delivery steps the roadmap calls for; a typed step is
self-documenting, carries the shared envelope (secrets/masking/isolation), and fails clearly when the tool
is missing — unlike an opaque `run:` line.

**Independent Test**: A `deploy:` step produces a `logos deploy …` invocation with the given args; a
`render:`/`uat:` step produces `zosn render …` / `euri test …`.

**Acceptance Scenarios**:

1. **Given** a `render`/`deploy`/`uat` step with args, **When** it runs, **Then** it invokes the tool's
   binary and subcommand followed by those args, through the same process machinery as every other step.
2. **Given** the same step declared in YAML and in the DSL, **When** both are parsed, **Then** they produce
   identical models.

---

### User Story 2 - A missing tool fails clearly (Priority: P2)

If the delivery tool binary isn't installed on the host, the step FAILS naming that tool, rather than
throwing.

**Why this priority**: These tools live in the khorum hub and may be absent on a given host; the failure
must be legible.

**Independent Test**: With the tool absent, the step is FAILED and the reason names the binary
(`zosn`/`logos`/`euri`).

**Acceptance Scenarios**:

1. **Given** a delivery step whose tool binary is missing, **When** it runs, **Then** it is FAILED and the
   reason names the binary.

### Edge Cases

- **No args**: a delivery step with empty args runs the bare `<binary> <subcommand>` (e.g. `euri test`).
- **Tool-specific flags**: the exact flag surface is the real tool's; `args` is a verbatim pass-through, so
  anything tool-specific is expressible without the engine hard-coding a flag contract.
- **Isolation**: a delivery step may set `image:` (024) to run the tool inside a container, and declares
  `secrets:` for tokens — both inherited from the shared step envelope.
- **Strict parsing**: an unknown key inside a `render`/`deploy`/`uat` block is rejected, like every other
  step type.

## Requirements *(mandatory)*

- **FR-001**: The descriptor MUST accept step-level `render:`, `deploy:`, and `uat:` keys (each
  `{ args: [ … ] }`), and the DSL MUST offer `renderStep`/`deployStep`/`uatStep`, producing identical
  models.
- **FR-002**: Each delivery step MUST run its khorum tool through the shared process machinery: `render`→
  `zosn render`, `deploy`→`logos deploy`, `uat`→`euri test`, followed by the pass-through `args`.
- **FR-003**: A delivery step MUST inherit the shared step envelope — secrets/masking, `workingDir`,
  `timeout`, and the 024 `image` container isolation — like the other typed steps.
- **FR-004**: A missing tool binary MUST surface as a FAILED step naming the binary, not an exception.
- **FR-005**: Tools are invoked as host CLIs (no new dependency); the strict parser MUST still reject unknown
  keys inside a delivery block.

## Success Criteria *(mandatory)*

- **SC-001**: `render`/`deploy`/`uat` steps invoke `zosn render` / `logos deploy` / `euri test` with the
  given args; YAML and DSL models are equal.
- **SC-002**: A delivery step carries the shared envelope (secrets/workingDir/timeout/image).
- **SC-003**: A missing tool binary FAILS the step naming the binary.
- **SC-004**: No new dependency; the strict parser rejects unknown keys; existing suites stay green.

## Assumptions

- **The tools are external (khorum hub).** Their exact flags live with the real `zosn`/`logos`/`euri` and
  are exercised on real delivery hosts; the engine wraps the conventional `<binary> <subcommand>` and passes
  `args` through verbatim, so it never hard-codes a flag contract it can't verify. This mirrors how
  `gradleStep`/`npmStep`/`dockerStep` wrap their tools.
- **One executor fronts the family.** Following the `DockerStep`/`NpmStep` precedent (one type + a mode
  enum + one executor), a single `HestiaStep` (tool enum + args) and `HestiaStepExecutor` back all three,
  while the descriptor keys and DSL functions give three distinct, semantic surfaces.
