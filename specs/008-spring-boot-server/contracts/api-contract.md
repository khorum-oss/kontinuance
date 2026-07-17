# Contract: Run-History Read API (v1) — unchanged from 007

This migration preserves the 007 contract byte-for-byte; see
[../../007-server-api/contracts/api-contract.md](../../007-server-api/contracts/api-contract.md).

- `GET /api/health` → 200 `{"status":"ok"}`
- `GET /api/runs?limit=N` → 200 `{"runs":[…]}` newest-first, default 50 / cap 500
- `GET /api/runs/{id}` → 200 `<run>` | 404 `{"error":"not found"}`
- Unknown path → 404; unsupported method → 405.

**Added (not a break):** the framework health/observability endpoint `GET /actuator/health` → 200 `{"status":"UP"}`.
No response shape or route under `/api` changes — existing consumers are unaffected (SC-001).
