# Phase 1 Data Model: Server / Read API

The API has no store of its own — it serves the 006 `RunRecord` over HTTP. Entities are the response
shapes and the handler result.

## Entities

### Run resource (JSON)
The API's serialization of a persisted `RunRecord`:
- `id: String`, `pipeline: String`, `status: String`, `failingStep: String?`, `reason: String?`,
  `startedAt: String?` (ISO-8601), `endedAt: String?`, `repo: String?`, `sha: String?`, `trigger: String?`.
- Invariant: exactly the persisted fields; no secret values (records carry none — FR-005).

### ApiResponse (handler result)
- `status: Int` (HTTP status), `json: String` (the response body).
- The transport-agnostic unit `RunApi` returns; `HttpApiServer` writes it to the wire (FR-008).

### Routes (the contract — see contracts/api-contract.md)
- `GET /api/health` → 200 `{"status":"ok"}`.
- `GET /api/runs?limit=N` → 200 `{"runs":[ <run>… ]}` newest-first, bounded.
- `GET /api/runs/{id}` → 200 `<run>` | 404 `{"error":"not found"}`.
- unknown route/method → 404/405 `{"error":…}` (FR-006).

## Relationships

```
HttpApiServer --routes--> RunApi --reads--> RunStore --yields--> RunRecord --serialized--> Run resource JSON
```

## Validation / rules
- `limit`: absent/invalid → default 50; clamped to max 500 (FR-002).
- unknown `id` → 404, distinct from 5xx (FR-003/SC-003).
- responses are JSON with a stable shape under `/api` (FR-010).
