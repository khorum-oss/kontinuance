# Quickstart: Server Authentication & Session

Validates the auth gate end to end. Details: [contracts/auth-api.md](./contracts/auth-api.md),
[data-model.md](./data-model.md).

## Prerequisites

- JDK 21. Run from the repo root. Sandbox Gradle is 8.14.3 (CI 9.5.1 is authoritative).

## Automated validation (the real boundary)

```bash
/opt/gradle/bin/gradle :server:test :server:detekt -Pdependency.env=public
```

Expected: green. Key suites:

- **`AuthCredentialsTest`** (unit) — constant-time match/reject; `enabled` true only when both username and
  password are set (both / only-one / neither).
- **`AuthIT`** (`@SpringBootTest(RANDOM_PORT)`, credentials set via `properties`) — over real HTTP:
  - `GET /api/runs` with no cookie → `401`; `GET /api/health` → `200` (public).
  - `POST /api/auth/login` wrong creds → `401`; correct → `200` + `Set-Cookie: KSESSION`.
  - `GET /api/runs` carrying the cookie → `200`.
  - `GET /api/auth/me` with cookie → `200` + `"username"`; without → `401`.
  - `POST /api/auth/logout` → cookie cleared; subsequent `GET /api/runs` with the old cookie → `401`.
- **`AuthOpenModeIT`** (`@SpringBootTest(RANDOM_PORT)`, no credentials) — `GET /api/runs` → `200` (open);
  `GET /api/auth/me` → `{"authRequired":false}`. Proves the existing suite/loopback usage is unaffected.

## Manual smoke (enforced mode)

```bash
KONTINUANCE_AUTH_USERNAME=operator KONTINUANCE_AUTH_PASSWORD=s3cret \
KONTINUANCE_STORE="$(pwd)/.local/runs" ./gradlew :server:run &

curl -i http://localhost:8077/api/runs                         # → 401 {"error":"authentication required"}
curl -i http://localhost:8077/api/health                       # → 200 (public)

curl -i -c cookies.txt -X POST http://localhost:8077/api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"operator","password":"s3cret"}'   # → 200 + Set-Cookie

curl -i -b cookies.txt http://localhost:8077/api/runs          # → 200
curl -i -b cookies.txt http://localhost:8077/api/auth/me       # → 200 {"username":"operator",…}
curl -i -b cookies.txt -X POST http://localhost:8077/api/auth/logout   # → 200, cookie expired
curl -i -b cookies.txt http://localhost:8077/api/runs          # → 401 (session invalidated)
```

Start it with **no** `KONTINUANCE_AUTH_*` and the server logs a warning that the API is unauthenticated and
every call above succeeds without a cookie (open mode).

## Success = the spec's SC-001…SC-006

Enforced calls without a session are rejected (SC-001); a signed-in operator reaches every endpoint
(SC-002); open mode leaves everything working with no login (SC-003); wrong user vs wrong password are
indistinguishable and `me` reflects state (SC-004); logout invalidates the session (SC-005); no new
dependency / `verification-metadata.xml` unchanged (SC-006).
