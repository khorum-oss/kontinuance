# Feature Specification: Publish-Artifacts Enablement

**Feature Branch**: `005-publish-artifacts`

**Created**: 2026-07-15

**Status**: Draft

**Input**: User description: "Make Kontinuance usable in a maintainer's own environment to publish build artifacts to their own private repository, by hand, today — on the already-installable `kontinuance` CLI. Deliver a native Kontinuance pipeline descriptor example that builds and publishes Maven artifacts to a configurable repository, with URL + credentials injected as masked secrets (never hard-coded), plus an examples/ directory and a quickstart. Descriptors must be authored from scratch in Kontinuance's own schema and must NOT be copied or derived from GitHub Actions or the hestia-systems descriptors. Verify end-to-end by publishing to a local file:// Maven repository through the CLI."

## User Scenarios & Testing *(mandatory)*

Kontinuance can already run pipeline descriptors from its installed CLI, but there is no ready example that does the one thing the maintainer wants next: **publish build artifacts to their own private repository, from their own machine, on demand.** This feature supplies a self-contained, native pipeline example plus the documentation to point it at a real private repository and run it — closing the gap between "the engine runs" and "I am using it to publish my artifacts." The "user" is a maintainer operating the `kontinuance` CLI in their own environment.

### User Story 1 - Publish artifacts to a private repository from the CLI (Priority: P1)

A maintainer has the `kontinuance` CLI installed. They take the provided example descriptor, set their repository URL and credentials as environment secrets, and run it. The pipeline builds their artifacts and publishes them to their private repository, reporting success or a clear failure.

**Why this priority**: This is the whole point of the feature — turning the installed engine into something that publishes real artifacts. Without it, nothing else here has value.

**Independent Test**: With credentials exported and the example pointed at a reachable Maven repository (a local `file://` repository suffices), running the descriptor through the CLI publishes the expected artifacts (JAR, POM, checksums) to that repository and the run reports success.

**Acceptance Scenarios**:

1. **Given** the example descriptor with the repository URL and credentials supplied as secrets, **When** the maintainer runs it through the CLI, **Then** the build artifacts are published to the target repository and the run's final status is success.
2. **Given** the same descriptor with a missing or unresolved credential secret, **When** it is run, **Then** it fails fast with a clear message and publishes nothing (no partial or unauthenticated upload).
3. **Given** a run that publishes, **When** its logs are read, **Then** no credential value appears in the output (secrets are masked).

### User Story 2 - Understand and adapt the example without touching engine code (Priority: P2)

A maintainer reads a quickstart that explains what the example does, which secrets it needs, and how to repoint it at their specific repository kind (e.g. Nexus, Artifactory, GitHub Packages, an S3-backed Maven repo). They adapt it by editing only the descriptor and environment — no engine changes.

**Why this priority**: Adoption depends on the example being understandable and adaptable. It builds on US1 but is not required to prove publishing works.

**Independent Test**: Following only the quickstart, a maintainer can change the target repository and the built artifact and run a successful publish without modifying any Kontinuance source.

**Acceptance Scenarios**:

1. **Given** the quickstart, **When** a maintainer follows it, **Then** they can identify every secret the example requires and where to set it.
2. **Given** a different repository kind, **When** the maintainer edits only the descriptor/secrets per the quickstart, **Then** the publish still runs without any engine/source change.

### User Story 3 - Descriptors are native and free of GitHub-YAML provenance (Priority: P2)

A maintainer (or reviewer) inspects the example and confirms it is written entirely in Kontinuance's own descriptor schema, with nothing copied or adapted from GitHub Actions workflows or from the hestia-systems delivery descriptors.

**Why this priority**: An explicit maintainer requirement — keeping the descriptors free of GitHub-YAML coupling avoids future divergence, confusion, and provenance concerns. It is a correctness/hygiene guarantee over the deliverable.

**Independent Test**: The example descriptor uses only documented Kontinuance schema keys and contains no GitHub Actions constructs; a review confirms it was authored from scratch, not derived from an external GitHub source.

**Acceptance Scenarios**:

1. **Given** the example descriptor, **When** it is inspected, **Then** it contains only Kontinuance-native schema (pipeline/stages/steps and step payloads) and none of GitHub Actions' constructs (e.g. `on:`, `jobs:`, `uses:`).
2. **Given** the example, **When** its provenance is reviewed, **Then** it is confirmed authored from scratch and not copied/derived from the hestia-systems descriptors or any external GitHub YAML.

### Edge Cases

- The target repository is unreachable or rejects the upload (bad URL, network, auth): the run fails with a clear, actionable message and does not report success.
- A credential secret is present but wrong: publishing fails at the upload step; the failure names the failing step and the secret value is never shown.
- The maintainer runs `--check` on the example: it validates and prints the structure without executing or contacting any repository.
- The artifact has nothing to publish (empty/misconfigured build): the run surfaces a clear failure rather than silently "succeeding" with no upload.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST provide a runnable example pipeline descriptor that builds and publishes Maven artifacts (at least a JAR plus its POM and checksums) to a configurable Maven repository.
- **FR-002**: The example MUST take the target repository URL and credentials from injected secrets/environment, never hard-coded in the descriptor.
- **FR-003**: Credential values MUST be masked in all run output.
- **FR-004**: A missing or unresolved required credential MUST cause a fail-fast error with no upload attempted, per the engine's existing secret-resolution behavior.
- **FR-005**: The example descriptor MUST be authored entirely in Kontinuance's native descriptor schema and MUST NOT contain GitHub Actions constructs or be copied/derived from GitHub Actions workflows, the hestia-systems descriptors, or any external GitHub YAML.
- **FR-006**: The project MUST include a quickstart that explains what the example does, enumerates the required secrets, and shows how to repoint it at common private-repository kinds (e.g. Nexus, Artifactory, GitHub Packages, S3-backed Maven) and run it.
- **FR-007**: The example and quickstart MUST live in a discoverable location (an `examples/` directory) and be runnable through the installed `kontinuance` CLI, including `--check` validation without side effects.
- **FR-008**: Adapting the example to a different repository or artifact MUST require editing only the descriptor and environment — no Kontinuance engine/source changes.
- **FR-009**: The publish path MUST be verified end-to-end at least once against a local `file://` Maven repository, demonstrating real artifacts landing in the repository.
- **FR-010**: This feature MUST NOT modify the pipeline engine's public contract or runtime behavior; it is example + documentation on top of the existing engine (any engine change would be a separate feature).

### Key Entities

- **Publish example descriptor**: a native Kontinuance pipeline that builds then publishes artifacts; parameterized by secrets.
- **Publish secrets**: the repository URL and credentials, supplied via environment and masked in logs.
- **Target Maven repository**: the destination (private repo or a local `file://` repo for verification) receiving the artifacts.
- **Quickstart guide**: the documentation that makes the example understandable and adaptable.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A maintainer with the CLI installed can publish artifacts to a reachable Maven repository using only the provided example + quickstart, with zero changes to Kontinuance source.
- **SC-002**: In an end-to-end verification run, the expected artifacts (JAR, POM, checksums) are present in the target `file://` repository after the pipeline reports success.
- **SC-003**: Across all publish runs, the raw credential value appears in run output 0 times.
- **SC-004**: A run with a missing credential secret fails fast and uploads nothing (0 artifacts) in 100% of cases.
- **SC-005**: The example descriptor contains 0 GitHub Actions constructs and is confirmed to have no copied/derived provenance from GitHub YAML.
- **SC-006**: Repointing the example at a different repository kind requires editing only the descriptor + environment (0 engine/source files changed).

## Assumptions

- The maintainer's private repository is Maven-compatible and reachable from their environment, and they can supply its URL + credentials as environment values.
- The project being published exposes a standard build-and-publish task (e.g. a Gradle publish task); the example drives that task rather than reimplementing publishing.
- Signing (GPG) of artifacts is available when the maintainer's repository requires it, supplied via the same secret mechanism; unsigned publishing is acceptable for repositories that do not require signatures.
- A local `file://` Maven repository is an acceptable stand-in for verifying the publish mechanism end-to-end without depending on external infrastructure.
- The engine's existing `run`/typed step types and secret masking are sufficient to express publishing; no new step type is required for this feature.
