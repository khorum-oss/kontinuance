# Contract: Web UI API (v1)

The UI consumes the existing read API unchanged, plus four **new additive stub endpoints** for the
forward-looking screens. Stubs return a stable typed shape (fixture data now) so the screens switch to real
sources later without UI change. All responses are `application/json`.

## Existing (real) — unchanged from 007/008

- `GET /api/health` → `{"status":"ok"}`
- `GET /api/runs?limit=N` → `{"runs":[<run>…]}` newest-first, default 50 / cap 500
- `GET /api/runs/{id}` → `<run>` | 404 `{"error":"not found"}`
- `GET /api/runs/stream` → `text/event-stream` of `run` events (each data = a run JSON)
- `GET /ws/runs` → WebSocket, JSON text frames (a run per frame)

A run object: `{ "id", "pipeline", "status", "repo"?, "sha"?, "trigger"?, "recordedAt"? }`.

## New (stub) — added this feature

- `GET /api/runs/{id}/pipeline` → `{ "runId", "stages":[ { "id","name","tasks":[ { "id","name","tool",
  "status","progress","deps":[id…] } ] } ] }`
  - `tool` ∈ {git,gradle,maven,env,cache,lint,bun,oci,nexus,argo}; `status` ∈ {pending,running,success,failed,skipped}; `progress` 0–100.
- `GET /api/deploy` → `{ "nodes":[ { "id","label","title","status","meta" } ], "artifacts":[ { "kind","name","digest","state" } ], "environment": { "podsReady","syncRevision","health","meta" } }`
- `GET /api/coverage` → `{ "tool":"kover", "line": { "pct","covered","total" }, "branch": { "pct","covered","total" }, "classes": <int>, "modules":[ { "name","kind","linePct","branchPct","missed" } ] }`
  - Coverage is **Kover** (the project's tool). The stub reads `build/reports/kover/report.xml` when present, otherwise serves fixture data of the same shape.
- `GET /api/config` → `{ "source":"kontinuance.yml", "text": <string>, "plan": { "stages","tasks","maxParallel","toolchain","publish","deploy" } }`

## Guarantees

- Additive only: no existing route or response shape changes (SC-006).
- Stub controllers add **no new JVM dependency** → `gradle/verification-metadata.xml` untouched,
  dependency verification stays enabled (FR-009).
- Errors: unknown path → 404; a stub failure → 500 `{"error":…}`; same conventions as the read API.
