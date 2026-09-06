# Feature 041: Repo-hosted descriptors

**Status**: Designed, unbuilt · **Depends on**: 027 (editable config), 032 (project registry),
033 (project source), 034 (commit-SHA checkout), 039 (project auto-registry), 040 (GitHub connect)

## Problem

Registering a project requires pasting a whole pipeline descriptor into a form. The operator must
already have the YAML in hand, in a text box, in a browser — before Kontinuance will accept that the
project exists. The descriptor then lives on the server, disconnected from the code it builds: it is
not reviewed, not versioned alongside the source, and not visible to anyone reading the repository.

Every other CI system in common use reads its pipeline definition out of the repository, at the commit
being built. Kontinuance already checks out that repository on every run (033) and can already pin a
run to an exact commit (034). The descriptor is the one input that still has to be supplied by hand.

The result is an Add Project screen whose main field is the thing the operator least wants to provide,
and a descriptor that drifts from the code it describes with nothing to reconcile them.

## What changes

A project can be connected with a name, a repository and a branch. Kontinuance reads
`kontinuance.yml` from that repository when the project runs.

A descriptor stored on the server still wins when one is present, so the paste flow remains available
for a repository nobody can commit to, and becomes the mechanism for overriding a repo's descriptor
without a commit.

The descriptor is fetched through the GitHub API rather than by cloning, which keeps `RunTrigger`'s
existing contract: the pipeline is fully resolved and parsed **before** a run record exists, so a
missing or malformed descriptor is a clean rejection and never a run that starts and then discovers it
has nothing coherent to do.

## Model

A project is a descriptor text at `<dir>/<name>.yml` plus an optional `{repo, branch}` sidecar (032,
033). This feature makes the descriptor text **optional**; its presence is the mode. Nothing new is
persisted.

| `<name>.yml` | source sidecar | Mode |
| --- | --- | --- |
| absent | present | **repo-hosted** — the descriptor is fetched from the repository |
| present | present | **overridden** — the stored descriptor wins; no network call |
| present | absent | unchanged from 032 |
| absent | absent | a derived project (039); still not runnable |

## Requirements

- **FR-001**: `GitHubClient` gains `fileAt(repo, path, ref): String?` — one file's contents at one ref,
  `null` when absent. It is the only new capability required of the GitHub seam.
- **FR-002**: A repository URL can be resolved to a `RepoRef`, yielding `null` for a URL that is not a
  GitHub repository. That `null` is the signal to require a stored descriptor rather than an error.
- **FR-003**: A single `DescriptorResolver` answers "what pipeline does this project run?" for every
  caller **on the manual trigger path** (the event source keeps its own resolution — see Out of
  scope). A stored descriptor short-circuits it with no network call; otherwise the project's source
  is resolved to a commit SHA, the descriptor is fetched at that SHA, and both are returned together.
- **FR-004**: The checkout is pinned to the exact commit the descriptor was read from, so a run's
  descriptor and its code always come from one commit. This uses 034's existing SHA pinning through
  `ProjectSourceInjector`, which requires no change.
- **FR-005**: Descriptor resolution failure is reported **before** a run record is created, naming the
  cause: a non-GitHub repository, no resolvable token, an unknown branch, an absent descriptor file, a
  parse error, or an unreachable API.
- **FR-006**: A project with a source but no stored descriptor is runnable. This reverses 039's rule
  that a project without a descriptor cannot be triggered.
- **FR-007**: Adding a project attempts to resolve its descriptor and reports what it found — the
  pipeline's name and stage count on success, a specific warning otherwise — but **creates the project
  either way**. An operator may register a project before the file exists or before a token is set.
- **FR-008**: The Config screen reports where the descriptor it is showing came from. A repo-hosted
  descriptor is read-only until edited; saving an edit writes the stored descriptor, which creates a
  visible override, and a revert action deletes it to return to the repository's copy.
- **FR-009**: The descriptor is read from `kontinuance.yml` at the repository root, changeable
  server-wide via `kontinuance.project.descriptorPath`.
- **FR-009a**: The server's live descriptor file (`kontinuance.config.descriptor`) keeps working. A
  server with **no active project** runs it exactly as it does today, so a plain single-descriptor
  deployment per `docs/getting-started.md` is unaffected. The full resolution order is: the active
  project's stored descriptor, else the active project's repository, else the live descriptor file,
  else a rejection. `DescriptorResolver` reads the active project's stored descriptor directly rather
  than relying on activation having mirrored it into the live file.
- **FR-010**: A repo-hosted project requires a branch, so no "default branch" lookup is needed. The
  Add Project form prefills `main`.
- **FR-011**: The GitHub token is resolved through 040's existing `GitHubTokenStore`, preserving its
  precedence rule (an environment variable named by `tokenEnv` beats a stored token) and its guarantee
  that no read returns a token value.
- **FR-012**: No new dependency in any module.

## Out of scope

- **The event-source path.** `EventSource` resolves a descriptor to a local `Path` through
  `TriggerResolver`, entirely separately from `RunTrigger`. Push and pull-request builds keep doing
  that. `DescriptorResolver` is shaped to be reusable there, but wiring it in — and deciding whether a
  pull-request build reads its descriptor from the PR head or the base — is its own feature.
- **Non-GitHub repositories.** A project whose repo URL is not a GitHub repository requires a stored
  descriptor. Cloning to read a descriptor, which would be host-agnostic, is deliberately not done: it
  would move descriptor resolution after the checkout and cost the parse-before-record guarantee in
  FR-005.
- **Per-project descriptor paths.** One server-wide path (FR-009). A project needing a different
  filename uses a stored descriptor.
- **Caching fetched descriptors.** Every trigger re-resolves. One small API call per run is cheaper
  than an invalidation rule.

## Verification

- `RepoRefParseTest` — the URL forms a stored source realistically holds (with and without `.git`,
  with and without a trailing slash, `git@` form), and `null` for a non-GitHub host.
- `DescriptorResolverTest` — a stored descriptor wins without touching the client; the repo path
  resolves branch → SHA → text → pipeline; a server with no active project falls back to the live
  descriptor file (FR-009a); and one case per failure row in FR-005. Uses the existing `GitHubClient`
  fake.
- `RestGitHubClientIT` — `fileAt` returns contents, and `null` on 404, against the stand-in HTTP
  server. No real network.
- `RunTriggerTest` — a resolution failure records **no** run; a successful repo-hosted trigger pins the
  resolved SHA onto the checkout.
- `ProjectControllerTest` — adding a project with an unresolvable descriptor still creates it and
  returns the warning (FR-007).
- Web unit tests — descriptor origin and override state render correctly.
- `app.spec.ts` — the reduced Add Project form connects a project with no descriptor and shows the
  resolution result; the Config screen shows provenance, raises the override banner on save, and
  reverts. The existing 039 case asserting that a project without a descriptor cannot be triggered is
  updated to reflect FR-006.
