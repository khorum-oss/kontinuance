# Contract: Run Logs

## `GET /api/runs/{id}/logs`

Returns the recorded, **already-masked**, step-prefixed output lines for run `{id}`, in production order.

```
HTTP/1.1 200 OK
Content-Type: application/json

{"runId":"<id>","lines":["[build] compiling…","[build] done","[test] 12 passed"]}
```

- A run with **no recorded output** (including an unknown id) returns `200` with `"lines":[]` — the log is
  "empty", never a 404 (the run record itself still 404s via `GET /api/runs/{id}`).
- Lines are verbatim what the engine's masking sink emitted (secret values already redacted). The endpoint
  neither re-masks nor reformats.
- When the run is protected by auth (016), this endpoint requires a session like the rest of `/api/**`.

## Engine `run()` override

```kotlin
suspend fun run(
    pipeline: Pipeline,
    secrets: SecretSource = EnvSecretSource(),
    completedStages: List<StageRun> = emptyList(),
    logSink: LogSink? = null,   // NEW — override the engine's default sink for THIS invocation
): Run
```

`null` (the default) keeps the engine's configured sink (stdout in the CI service / tests) — existing callers
are unchanged. The server passes a per-run recording sink so a run's output lands in its own log.

## `RunLogStore` (persistence)

```kotlin
interface RunLogStore {
    fun append(runId: String, line: String)   // one masked line
    fun read(runId: String): List<String>      // ordered; empty when none
}
```

`FileRunLogStore(dir)` writes `<dir>/<runId>.log` (append, one line per row); `InMemoryRunLogStore` for tests.
Run id is sanitised to a safe filename. Isolation is by id — concurrent runs write distinct files.

## Web

`api.getRunLogs(id): Promise<string[]>` → the `lines`. The run-detail panel renders them (monospace,
in order) with an explicit empty state; while the run status is non-terminal the page re-fetches on a short
interval and stops once terminal.
