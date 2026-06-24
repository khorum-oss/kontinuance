# Kontinuance — Architecture Overview

Kontinuance is a CI/CD platform written in Kotlin (Spring Boot) — an effort to
build a self-hostable Jenkins / GitHub Actions replacement. This document captures
the architectural direction the constitution defers to.

## Core Architectural Decisions

The fundamental split in any CI/CD engine, kept as separate concerns from day one
(even if they share a single process at first):

```
Orchestrator (controller plane)
└── schedules pipelines, tracks state, manages queues

Agent/Runner (data plane)
└── actually executes steps — isolated, ephemeral

Event Source
└── webhooks, polling, manual triggers
```

These three can live in one process initially, but must be designed as separate
concerns from the start.

## Where Kotlin + Spring Shine

**Orchestrator** fits Spring Boot well:

- Spring State Machine or a hand-rolled FSM for pipeline/step lifecycle
- Coroutines for non-blocking step coordination without callback hell
- Spring Integration or simple Kafka consumers for event ingestion
- R2DBC or JDBC for pipeline state persistence

**Agent/Runner** execution model options:

| Approach | Tradeoff |
| --- | --- |
| Embedded coroutine-based executor | Simple, no isolation — good for v0 |
| Docker SDK (via `docker-java` or raw HTTP) | Real isolation, more ops overhead |
| Kubernetes Jobs | Scalable, but heavy dependency |
| Process-based via `ProcessBuilder` | Surprisingly viable, low overhead |

For a drop-in replacement targeting teams without k8s, Docker-based runners hit the
sweet spot (later phase); v0 starts in-process / `ProcessBuilder`.

## The Pipeline DSL

Three options were considered, tied to the existing Konstellation interpreted-vs-codegen
DSL thinking:

**Option A — Interpreted Kotlin DSL** (like a Jenkinsfile):

```kotlin
pipeline {
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
```

Lambda-with-receiver, fully in-source, no codegen, evaluated at runtime.

**Option B — YAML/TOML descriptor + Kotlin runtime.** Closer to GitHub Actions,
easier for non-Kotlin teams. Konstellation could generate the Kotlin runner config
from descriptors.

**Option C — Hybrid.** YAML for simple pipelines, with an escape hatch to the Kotlin
DSL for complex ones. Mirrors how Gradle uses convention plugins.

**Decision:** Kontinuance adopts **Option C (hybrid)**, built on the **latest
Konstellation meta-DSL**.

## Things To Watch Out For

**Isolation is the hard problem.** Steps must not pollute each other. Without
containers you manage: working-directory scoping, environment-variable leakage,
process cleanup on failure/timeout, and secret masking in logs. Jenkins got this
wrong for years — don't underestimate it.

**State machine complexity.** Pipeline states explode fast; model them explicitly
with a sealed-class hierarchy from day one:

```
PENDING → QUEUED → RUNNING → SUCCESS
                           → FAILED
                           → CANCELLED
                           → TIMED_OUT
                           → SKIPPED (conditional steps)
                           → WAITING_ON_APPROVAL
```

```kotlin
sealed class PipelineStatus {
    data object Pending : PipelineStatus()
    data object Running : PipelineStatus()
    data class Failed(val step: String, val reason: String) : PipelineStatus()
    // ...
}
```

**Log streaming.** Real-time log tailing gets complicated fast. Use WebSockets or SSE
from the orchestrator, with logs written to an append-only store (flat files per run
work fine initially). Avoid streaming through the database.

**Webhook reliability.** GitHub webhooks retry on failure — the endpoint must respond
`200` fast and hand off to an internal queue immediately. Any blocking in the webhook
handler causes duplicate runs.

**Secret handling.** Never store secrets in the pipeline DSL or DB in plaintext.
Design secret injection as a first-class concern early — even if v0 just reads from
env vars, the interface must abstract it so Vault can plug in later.

**Concurrency controls.** Max parallel jobs per runner, pipeline-level concurrency
(cancel-on-new-push semantics), and queue-depth limits. Coroutines with `Semaphore`
and structured concurrency handle most of this elegantly.

## Potential Tech Stack

```
Spring Boot (Kotlin)        ← orchestrator + API
Coroutines                  ← async execution model
Exposed or JOOQ             ← pipeline state (Postgres)
docker-java or Ktor client  ← runner agent communication
SSE / WebSockets            ← log streaming
GraalVM native              ← agent sidecar CLI (hermes-brief pattern)
Kafka (optional, later)     ← event bus for scale
```

## Suggested Phasing

- **v0** — In-process executor, `ProcessBuilder` steps, single pipeline definition,
  logs to stdout.
- **v1** — Docker-based isolation, persistent state, webhook trigger, basic UI.
- **v2** — Multi-agent, secret management, parallel steps, DSL maturity.
- **v3** — Plugin system, artifact storage, caching.

## Diagrams

Architecture and flow diagrams (Mermaid) live under [`docs/diagrams/`](./diagrams/):

- [User flow](./diagrams/user-flow.md) — defining and running a pipeline.
- [Class diagram](./diagrams/class-diagram.md) — the v0 engine model + step-type seam.
- [Sequence](./diagrams/sequence.md) — a pipeline run execution.
- [C4](./diagrams/c4.md) — system context and container views.
