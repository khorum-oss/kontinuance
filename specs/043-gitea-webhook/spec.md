# Feature Specification: Gitea Webhook Receiver

**Feature Branch**: `feat/043-gitea-webhook`

**Created**: 2026-10-12

**Status**: In progress — receiver built; reporting, publishing and the start-up reconcile are not

**Input**: User: "I need to rework relikquary and kontinuance to not poll for kontinuance since we are
inverting the control. Gitea as source and updates github on merge to main."

Until now an event reached Kontinuance by being fetched: the poll loop (003) asked GitHub what had
changed, or a GitHub Actions job called `POST /api/ci/dispatch` (the dispatch gate). Both assume GitHub is
where code is reviewed and merged. This feature is the other direction: a self-hosted Gitea is the source
of truth, and it *tells* Kontinuance — a signed webhook — when a pull request or the tracked branch
changes.

This spec covers the whole inversion; it is being built in slices. The first slice — the receiver — is
additive: the poll loop and `/api/ci/dispatch` are untouched, and nothing happens until a host configures
a webhook secret.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A pull request in Gitea is checked without anyone polling (Priority: P1) — built

**Acceptance Scenarios**:

1. **Given** a configured repository, **When** Gitea delivers `pull_request` with action `opened`,
   `reopened` or `synchronized`, **Then** the repository's `prPipeline` runs at `pull_request.head.sha`
   and the run records `gitea-webhook` as its trigger.
2. **Given** a pull request whose head repository is not the configured repository — or is not stated —
   **Then** nothing runs (`204`). The head commit is built on the host; unknown provenance fails closed.
3. **Given** any other action (`closed`, `labeled`, …) or any other event, **Then** nothing runs (`204`).

### User Story 2 - A merge delivers, and a replayed delivery does not (Priority: P1) — built

1. **Given** a `push` to `refs/heads/<trackedBranch>` whose `after` is the branch's current head in
   Gitea, **Then** the repository's `pushPipeline` runs at that commit.
2. **Given** a `push` whose `after` is no longer the head, **Then** nothing runs (`204`). A signature
   carries no timestamp, so an old delivery can be re-sent; asking Gitea is what stops an old merge being
   redelivered.
3. **Given** Gitea cannot be asked (no `api-url`, no answer), **Then** the push is refused (`400`) with the
   reason. Pull-request deliveries do not need the lookup and still run.
4. **Given** a push to any other ref, or a deletion of the tracked branch, **Then** nothing runs (`204`).

### User Story 3 - Only Gitea can start a run this way (Priority: P1) — built

1. **Given** no `X-Gitea-Signature`, a malformed one, or one made with another secret, **Then** `401`.
2. **Given** no secret is configured, **Then** every delivery is `401`.
3. **Given** a correctly signed delivery for a repository outside the allow-list, **Then** `400` naming it.

### User Story 4 - Gitea shows the verdict (Priority: P1) — not built

A webhook-started run posts `pending` and then its terminal state to Gitea's commit-status API, as
`kontinuance/ci` for a pull request and `kontinuance/deliver` for a push, so branch protection in Gitea can
require the check.

### User Story 5 - Kontinuance publishes the tracked branch (Priority: P2) — not built

A binding may carry a `publish:` block (a remote and a branch). On a push to the tracked branch Kontinuance
pushes that one commit to that one ref before running the pipeline, and reports it as
`kontinuance/publish`. One commit, one ref: a private repository's other branches never leave.

### User Story 6 - A delivery missed while the server was down is not lost (Priority: P2) — not built

Once, at start-up, for each binding with a `pushPipeline`: if Gitea's tracked-branch head is not the commit
of the last successful delivery, dispatch it. Not a poll loop — it runs once.

## Requirements

- **FR-001** `POST /api/hooks/gitea` authenticates by `X-Gitea-Signature`: the hex HMAC-SHA256 of the raw
  body under `kontinuance.hooks.gitea.secret`, verified over the bytes as received and compared in
  constant time. It is the one route outside the session / CI-token gate.
- **FR-002** A delivery never selects a descriptor. It resolves to `(repo, sha, event)` and goes through
  `CiDispatcher`, so the allow-list, the sha pin and the in-flight de-duplication apply unchanged.
- **FR-003** `RepositoryBinding` carries `cloneBase` (source-level, overridable per repository; default
  `https://github.com`). A dispatch checks out from the binding's host instead of assuming GitHub.
- **FR-004** Responses: `202` started, `200` already running, `204` not a reason to build (the reason in
  `X-Kontinuance-Ignored`), `400` should have built and could not, `401` not authenticated.

## Decisions

- **An unconfigured secret refuses deliveries; it does not refuse start-up.** The receiver ships to a host
  before that host has a secret, and a server that will not boot is a worse failure than an endpoint that
  answers `401`.
- **The replay guard is a question to Gitea, not a remembered delivery id.** A remembered id is lost on
  restart and would also swallow Gitea's own *Redeliver* button, which is the manual recovery path.
- **`synchronize` is accepted alongside Gitea's `synchronized`**, so a spelling difference between
  versions cannot silently stop a pull request being re-checked.

## Not verified against a real Gitea

The payload fields and the `synchronized` spelling are from Gitea's documented webhook format; the
integration test stands in for Gitea with a stub. The first delivery from a real instance is the proof.
