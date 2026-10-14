# Feature Specification: Gitea Webhook Receiver

**Feature Branch**: `feat/043-gitea-webhook`

**Created**: 2026-10-12

**Status**: In progress — receiver, status reporting, publishing and the start-up reconcile built; removing the poll loop is not

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
   and the run records `gitea-pr` as its trigger (`gitea-push` for a delivery).
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

### User Story 4 - Gitea shows the verdict (Priority: P1) — built

A webhook-started run posts `pending` and then its terminal state to Gitea's commit-status API, as
`kontinuance/ci` for a pull request and `kontinuance/deliver` for a push, so branch protection in Gitea can
require the check.

1. **Given** a webhook-started run, **Then** Gitea receives `pending` once, then exactly one verdict:
   `success`, `failure` (failed or timed out) or `error` (cancelled, or an unexpected end).
2. **Given** a run paused at an approval gate, **Then** it stays `pending` until it is resumed and ends.
3. **Given** a run started any other way (manual dispatch, the dashboard, the poll loop), **Then** nothing
   is posted to Gitea.
4. **Given** Gitea cannot be reached, **Then** the post is retried, then logged; the run is unaffected.
5. **Given** `kontinuance.hooks.gitea.run-url-base` is set, **Then** each status links to the run.

### User Story 5 - Kontinuance publishes the tracked branch (Priority: P2) — built

A binding may carry a `publish:` block (a remote and a branch). On a push to the tracked branch Kontinuance
pushes that one commit to that one ref, and reports it on the commit as `kontinuance/publish`. One commit,
one ref: a private repository's other branches never leave.

1. **Given** a binding with `publish:`, **When** a push delivery for the tracked branch's current head
   arrives, **Then** that commit is pushed to `publish.branch` on `publish.remote`, and Gitea receives
   `pending` then `success` as `kontinuance/publish`.
2. **Given** the source has other branches or tags, **Then** none of them reach the remote.
3. **Given** the remote has a commit the source does not, **Then** the push is refused, the remote is
   unchanged, and the status is `failure` with git's reason. Nothing is forced.
4. **Given** a binding with `publish:` and no `pushPipeline`, **Then** the commit is published and no run
   starts (`204`).
5. **Given** a binding with no `publish:` block, **Then** nothing is pushed anywhere.
6. **Given** publishing fails, **Then** delivery still runs: the two are reported separately.

### User Story 6 - A delivery missed while the server was down is not lost (Priority: P2) — built

Once, at start-up, for each binding with a `pushPipeline` or a `publish:` block, Kontinuance asks Gitea
for the tracked branch's head. Not a poll loop — it runs once.

1. **Given** no run of the binding's push pipeline has succeeded at the head, **Then** it is dispatched
   through `CiDispatcher`, recorded as `gitea-reconcile`, and reports as `kontinuance/deliver`. A head whose
   last delivery failed counts as not delivered.
2. **Given** a successful run of that pipeline at the head, however it was started, **Then** nothing runs.
3. **Given** the binding publishes, **Then** the head is published. Publishing a commit already on the
   remote changes nothing.
4. **Given** Gitea cannot answer for one binding, **Then** that binding is logged and the rest proceed.
5. **Given** no `api-url`, or `kontinuance.hooks.gitea.reconcile-on-start: false`, **Then** nothing happens.

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
- **FR-005** Publishing pushes with `git push <remote> <sha>:refs/heads/<branch>` from a scratch repository
  that only ever fetches the tracked branch, and only a commit that is on that branch. It is never forced.
  The token (`kontinuance.publish.token`) reaches git through a credential helper reading the child's
  environment — never a URL or an argument — and a `publish.remote` that embeds a credential is rejected
  when the config is read.

## Decisions

- **An unconfigured secret refuses deliveries; it does not refuse start-up.** The receiver ships to a host
  before that host has a secret, and a server that will not boot is a worse failure than an endpoint that
  answers `401`.
- **The replay guard is a question to Gitea, not a remembered delivery id.** A remembered id is lost on
  restart and would also swallow Gitea's own *Redeliver* button, which is the manual recovery path.
- **`synchronize` is accepted alongside Gitea's `synchronized`**, so a spelling difference between
  versions cannot silently stop a pull request being re-checked.

- **The reporter watches the run store instead of being called by the dispatcher.** A run can end by
  being resumed from an approval gate by a different request; every such road ends in a store write.
- **A run still `Running` when the server stops leaves a `pending` status behind.** Nothing resumes it.
  Gitea's *Redeliver* starts a fresh run, whose verdict replaces it. The start-up reconcile (User Story
  6) does not cover pull requests.

- **A publish is queued, not awaited, and does not gate delivery.** A fetch and a push outlast the time a
  webhook sender waits for its answer. One consumer publishes in arrival order, so two quick merges reach
  the remote in order. A failed publish is its own red status; it does not stop the delivery run.
- **Publishing runs only from a webhook push.** A manual `/api/ci/dispatch` with `event: push` delivers
  without publishing. The start-up reconcile (User Story 6) is what covers a missed merge.
- **A publish interrupted by a restart leaves `kontinuance/publish` pending.** Redelivering the push from
  Gitea publishes it again; publishing a commit twice is harmless.

- **A delivery counts by pipeline name, not by trigger.** Deliveries started by the GitHub dispatch
  before cutover are recognised, so the first restart after cutover does not redeliver what stage has.
- **A run left `Running` by a restart blocks its own reconcile.** Nothing marks an interrupted run as
  ended, so the in-flight guard treats it as still building and the reconcile logs it instead of
  dispatching. Redelivering from Gitea, or a manual dispatch once the record is cleared, is the remedy.

## Not verified against a real Gitea

The payload fields and the `synchronized` spelling are from Gitea's documented webhook format; the
integration test stands in for Gitea with a stub. The first delivery from a real instance is the proof.

Publishing is tested against real git repositories on disk, which need no credential. The credential
helper, a fetch from a private Gitea and a push to GitHub have not run against the real hosts.
