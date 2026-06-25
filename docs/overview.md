Drop-in CI/CD for Kotlin (+ Spring Boot) — Josh's Opinionated Guide
Core Platform Choice
For your setup under khorum-oss and your Hestia self-hosted runner, GitHub Actions fits naturally:

Native GitHub integration (no extra auth plumbing)
Your Hestia runner already handles heavier jobs
Konstellation already has CI/CD work-in-progress — a drop-in template can live there


The Recommended Pipeline Shape
push / PR
└── build.yml
├── [check] detekt + ktlint
├── [test] gradlew test
├── [verify] dependency-verification + verifyVersions
├── [build] gradlew build / bootJar
└── [publish] (on main) → GHCR or Maven

Key Things to Get Right
1. Gradle Caching — Critical for Speed
   yaml- uses: actions/cache@v4
   with:
   path: |
   ~/.gradle/caches
   ~/.gradle/wrapper
   key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle.kts', '**/libs.versions.toml') }}
   restore-keys: |
   ${{ runner.os }}-gradle-
   Without this, cold builds on every run will kill you.
2. Your Dependency Verification Setup
   Your verification-metadata.xml + armored keyring setup means the pipeline must carry the keyring or disable verification selectively. Two approaches:

Store the keyring as a GitHub Actions secret and write it to disk at job start
Use --no-verify-metadata only for isolated jobs that don't publish (risky)

Prefer the first. Something like:
yaml- name: Import PGP keyring
run: echo "${{ secrets.GRADLE_KEYRING }}" | base64 -d > $GRADLE_USER_HOME/gradle/trusted-key-server.gpg
3. Spring Boot Specifics
   If bootJar lives in the pipeline, separate concerns:

Test stage — runs unit + integration (Testcontainers via Docker-in-docker or your Hestia runner's native Docker)
Package stage — produces the fat jar, doesn't run tests again
Publish stage — pushes to GHCR with a tag strategy (semver tag or sha-${{ github.sha }})

Tag strategy matters for your DigitalOcean Docker Compose hosting guide approach.
4. The verifyVersions Task
   Wire it early — before tests — as a fast-fail gate:
   yaml- name: Verify dependency versions
   run: ./gradlew verifyVersions --no-daemon
   This catches drift against your recommended.toml on Reliquary before wasting build minutes.
5. Branch Protection + PGP Signing
   Your 1Password PGP signing workflow needs a CI identity. Options:

A dedicated bot GPG key stored as a secret (simpler)
1Password's GitHub Actions integration (1password/load-secrets-action) to pull the signing key at runtime (cleaner, fits your existing setup)

For branch protection rulesets, require:

Status checks: detekt, test, build all green
No force-push on main
Require signed commits if you sign locally

6. Testcontainers in CI
   On your self-hosted Hestia runner, Docker runs natively — no DinD needed. On GitHub-hosted runners, add:
   yamlservices:
   docker:
   image: docker:dind
   Or just route those jobs to Hestia via:
   yamlruns-on: self-hosted

Drop-in Template Structure (Konstellation-Friendly)
.github/
workflows/
build.yml          ← main pipeline
release.yml        ← tag-triggered publish
dependency-pr.yml  ← automated version bump PRs (GitHub App)
actions/
gradle-setup/      ← composite action: Java + Gradle cache + keyring
action.yml
The composite gradle-setup action gives every workflow a single uses: ./.github/actions/gradle-setup instead of repeating 20 lines — this makes it genuinely drop-in across Khorum repos.

Watch-outs Specific to Your Stack
RiskMitigationKSP annotation processing slows incremental buildsUse --build-cache + Gradle remote cache if you hit 3+ min buildsbootJar produces a fat jar that can hit GHCR layer cache missesLayer your Docker image: deps layer → app layerDetekt false positives blocking PRsRun detekt in --auto-correct on branch, fail only on mainVerification metadata gets stale after libs.versions.toml bumpsAdd a CI job that regenerates metadata and opens a PRSpring integration tests need a running contextUse @SpringBootTest with Testcontainers + DynamicPropertySource to avoid port conflicts

Want me to generate the actual build.yml YAML and composite gradle-setup/action.yml files tuned to your Khorum OSS setup? I can also wire in the release.yml for your GitHub App version-bump flow.You said: No.No. I am thinking of trying to build a jenkins/github actions replacement10:38 PMClaude responded: Ah, much more interesting.Ah, much more interesting. You want to build a CI/CD platform in Kotlin. Let me think through this properly.

Building a CI/CD Platform in Kotlin
Core Architectural Decisions First
The fundamental split in any CI/CD engine:
Orchestrator (controller plane)
└── schedules pipelines, tracks state, manages queues

Agent/Runner (data plane)
└── actually executes steps — isolated, ephemeral

Event Source
└── webhooks, polling, manual triggers
These three can live in one process at first, but design them as separate concerns from day one or you'll regret it.

Where Kotlin + Spring Shine Here
Orchestrator fits Spring Boot well:

Spring State Machine or a hand-rolled FSM for pipeline/step lifecycle
Coroutines for non-blocking step coordination without callback hell
Spring Integration or simple Kafka consumers for event ingestion
R2DBC or JDBC for pipeline state persistence

Agent/Runner deserves more thought — options:
ApproachTradeoffEmbedded coroutine-based executorSimple, no isolation — good for v1Docker SDK (via docker-java or raw HTTP)Real isolation, more ops overheadKubernetes JobsScalable, but heavy dependencyProcess-based via ProcessBuilderSurprisingly viable, low overhead
For a drop-in replacement targeting teams without k8s, Docker-based runners hit the sweet spot.

The Pipeline DSL — This Fits Your Wheelhouse Perfectly
Given your work on Konstellation and your existing thinking around idiomatic Kotlin DSLs vs codegen DSLs, you have a real architectural decision here:
Option A — Interpreted DSL (like Jenkinsfile)
kotlinpipeline {
stage("build") {
step("compile") {
run("./gradlew build")
}
}
stage("test") {
parallel {
step("unit") { run("./gradlew test") }
step("integration") { run("./gradlew integrationTest") }
}
}
}
Lambda-with-receiver, fully in-source, no codegen. Evaluated at runtime.
Option B — YAML/TOML descriptor + Kotlin runtime

Closer to GitHub Actions. Easier for non-Kotlin teams to adopt. Konstellation could generate the Kotlin runner config from descriptors.
Option C — Hybrid

YAML for simple pipelines, escape hatch to Kotlin DSL for complex ones. This mirrors what Gradle itself does with convention plugins.
Given your aesthetic, Option A or C probably fits best.

Things To Watch Out For
Isolation is the Hard Problem
Steps need to not pollute each other. Without containers, you're managing:

Working directory scoping
Environment variable leakage
Process cleanup on failure/timeout
Secret masking in logs

Don't underestimate this. Jenkins got it wrong for years.
State Machine Complexity
Pipeline states explode fast:
PENDING → QUEUED → RUNNING → SUCCESS
→ FAILED
→ CANCELLED
→ TIMED_OUT
→ SKIPPED (conditional steps)
→ WAITING_ON_APPROVAL
Model this explicitly with a sealed class hierarchy from day one:
kotlinsealed class PipelineStatus {
data object Pending : PipelineStatus()
data object Running : PipelineStatus()
data class Failed(val step: String, val reason: String) : PipelineStatus()
// ...
}
Log Streaming
Real-time log tailing feels simple, gets complicated fast. WebSockets or SSE from the orchestrator, with logs written to an append-only store (even flat files per run work fine initially). Avoid trying to stream through your database.
Webhook Reliability
GitHub webhooks retry on failure — your endpoint must respond 200 fast and hand off to an internal queue immediately. Any blocking in the webhook handler will cause duplicate runs.
Secret Handling
Never store secrets in your pipeline DSL or DB in plaintext. Design secret injection as a first-class concern early — even if v1 just reads from env vars, the interface should abstract it so you can plug in Vault later.
Concurrency Controls

Max parallel jobs per runner
Pipeline-level concurrency (cancel-on-new-push semantics)
Queue depth limits

Coroutines with Semaphore and structured concurrency handle most of this elegantly.

Potential Tech Stack
Spring Boot (Kotlin)       ← orchestrator + API
Coroutines                 ← async execution model
Exposed or JOOQ            ← pipeline state (Postgres)
docker-java or Ktor client ← runner agent communication
SSE / WebSockets           ← log streaming
GraalVM native (hermes-brief pattern) ← agent sidecar CLI
Kafka (optional, later)    ← event bus for scale
That last line connects nicely — your hermes-brief GraalVM native CLI sidecar pattern could directly inform how you build lightweight runner agents.

Suggested Phasing

v0 — In-process executor, ProcessBuilder steps, single pipeline definition, logs to stdout
v1 — Docker-based isolation, persistent state, webhook trigger, basic UI
v2 — Multi-agent, secret management, parallel steps, DSL maturity
v3 — Plugin system, artifact storage, caching


What angle draws you most — the DSL design, the execution model, or the orchestrator architecture? Happy to go deep on any of those.