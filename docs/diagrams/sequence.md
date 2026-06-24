# Sequence — Pipeline Run Execution (v0)

One execution of a pipeline: ordered stages/steps, concurrency-gated step launches,
isolated `ProcessBuilder` execution, secret-masked log streaming, and the
timeout/cancel paths.

```mermaid
sequenceDiagram
    actor Dev as Developer
    participant Eng as PipelineEngine
    participant Sem as Semaphore(K)
    participant Exec as StepExecutor
    participant PB as ProcessBuilder
    participant Mask as SecretMasker
    Dev->>Eng: run(pipeline, secrets)
    Eng->>Eng: validate pipeline
    loop each stage in order
      loop each step in order
        Eng->>Sem: acquire permit
        Eng->>Exec: execute(stepContext)
        Exec->>PB: start(command, isolated workdir, scoped env)
        PB-->>Exec: stdout/stderr stream
        Exec->>Mask: mask(line)
        Mask-->>Dev: masked log line (stdout)
        alt exit 0
          Exec-->>Eng: StepRun SUCCESS
        else exit non-zero
          Exec-->>Eng: StepRun FAILED(step, reason)
        else timeout exceeded
          Exec->>PB: destroyForcibly() + descendants()
          Exec-->>Eng: StepRun TIMED_OUT
        end
        Exec->>PB: cleanup workdir + process tree
        Eng->>Sem: release permit
        Eng-->>Dev: StatusEvent (via Flow)
      end
    end
    Eng-->>Dev: Run (final status)
    Note over Dev,Eng: cancel(runId) terminates in-flight steps, Run CANCELLED
```

Related: [`contracts/dsl-and-engine-api.md`](../../specs/001-pipeline-foundation/contracts/dsl-and-engine-api.md).
