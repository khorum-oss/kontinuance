---
description: "Task list for Publish-Artifacts Enablement"
---

# Tasks: Publish-Artifacts Enablement

**Input**: `/specs/005-publish-artifacts/` (spec.md, plan.md). Branch: `claude/kontinuance-cross-app-alignment-w3hk0o`.

**Tests**: the "test" is an end-to-end publish to a local `file://` repo through the CLI (Constitution II).

## Phase 1: Setup
- [ ] T001 Create the `examples/publish-artifacts/` directory structure.

## Phase 2: User Story 1 — Publish to a repository from the CLI (P1) 🎯 MVP
- [ ] T002 [US1] Author `examples/publish-artifacts/publish-artifacts.yaml` — native descriptor: a `build`
  stage (`cd "$PROJECT_DIR" && ./gradlew build`) and a `publish` stage (`cd "$PROJECT_DIR" && ./gradlew publish`),
  with `secrets: [PROJECT_DIR, PUBLISH_REPO_URL, PUBLISH_REPO_USER, PUBLISH_REPO_PASSWORD]`.
- [ ] T003 [US1] Create `examples/publish-artifacts/sample-lib/` (standalone `java-library` + `maven-publish`
  build) that publishes to `$PUBLISH_REPO_URL` with optional `$PUBLISH_REPO_USER/PASSWORD` creds.
- [ ] T004 [US1] Verify end-to-end: point `PROJECT_DIR` at `sample-lib`, `PUBLISH_REPO_URL` at a
  `file://` temp repo, run via the `kontinuance` CLI; assert the JAR + POM land in the repo and the run is Success.
- [ ] T005 [US1] Verify fail-fast + masking: run with a missing credential secret (fail, no upload); confirm
  no secret value appears in run output.

## Phase 3: User Story 2 — Understandable & adaptable quickstart (P2)
- [ ] T006 [US2] Write `examples/publish-artifacts/README.md`: what it does, the required secrets, how to run
  (`kontinuance --check` then run), and how to repoint at Nexus/Artifactory/GitHub Packages/S3-Maven.

## Phase 4: User Story 3 — Native, no GitHub-YAML provenance (P2)
- [ ] T007 [US3] Review the descriptor: only native schema keys, zero GitHub Actions constructs (`on:`/`jobs:`/`uses:`),
  authored from scratch (not derived from hestia-systems or any external GitHub YAML); note this in the README.

## Phase 5: Polish
- [ ] T008 Confirm the existing engine build/gates are untouched and green; update `docs/roadmap.md` to mark 005 built.
