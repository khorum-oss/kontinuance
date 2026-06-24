# User Flow — Defining & Running a Pipeline (v0)

How a developer goes from a pipeline definition to a result in Kontinuance v0.
Both definition front-ends (YAML descriptor, Kotlin DSL) converge on one model and
one execution engine.

```mermaid
flowchart TD
    A[Developer authors pipeline] --> B{Definition format}
    B -->|YAML descriptor| C[Parse descriptor]
    B -->|Kotlin DSL| D[Build pipeline model]
    C --> E{Valid?}
    D --> E
    E -->|No| F[Validation error: location reported, no steps run]
    E -->|Yes| G[PipelineEngine.run]
    G --> H[For each stage in order]
    H --> I[For each step in order]
    I --> J[StepExecutor runs command via ProcessBuilder in isolated workdir]
    J --> K[Stream logs to stdout, secrets masked]
    K --> L{Step outcome}
    L -->|exit 0| M[Step SUCCESS]
    L -->|exit non-zero| N[Step FAILED: stage stops, run FAILED]
    L -->|timeout| O[Step TIMED_OUT: kill process tree]
    L -->|cancelled| P[Step CANCELLED]
    L -->|condition unmet| Q[Step SKIPPED]
    M --> R{More steps or stages?}
    Q --> R
    R -->|Yes| H
    R -->|No| S[Run SUCCESS]
    N --> T[Run result reported]
    O --> T
    P --> T
    S --> T
```

Related: [`spec.md`](../../specs/001-pipeline-foundation/spec.md) user stories US1–US3.
