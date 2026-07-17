# Contract: Run-History Read API (v1)

Consumer-facing (the Web UI depends on it). Stable under the `/api` prefix; breaking the shape/routes
requires a MAJOR bump (Constitution I). JSON, read-only, no auth (private-network increment).

## GET /api/health
→ `200` `{"status":"ok"}`

## GET /api/runs?limit=N
List the most recent runs, newest-first. `limit` optional (default 50, max 500; invalid → default).
→ `200`
```json
{ "runs": [
  { "id": "run-1", "pipeline": "pr-check", "status": "Success",
    "startedAt": "2026-07-15T12:00:00Z", "endedAt": "2026-07-15T12:00:05Z",
    "repo": "khorum-oss/relikquary", "sha": "abc123", "trigger": "PULL_REQUEST" }
] }
```
Empty store → `{"runs":[]}` (still `200`).

## GET /api/runs/{id}
→ `200` a single run object (same fields as a list entry; `failingStep`/`reason` present when failed)
→ `404` `{"error":"not found"}` for an unknown id.

## Errors
- Unknown path → `404` `{"error":"not found"}`.
- Unsupported method on a known path → `405` `{"error":"method not allowed"}`.
- No secret values ever appear in any response.
