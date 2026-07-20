# Contract: Web ↔ Server Auth Flow

Consumes the 016 server contract ([specs/016-authentication/contracts/auth-api.md](../../016-authentication/contracts/auth-api.md)).
Same-origin; the HttpOnly `KSESSION` cookie is sent/received by the browser automatically (the UI never
reads it).

## Client methods (`web/src/lib/api/client.ts`)

| Method | Call | Returns / throws |
|---|---|---|
| `api.me()` | `GET /api/auth/me` | `Session`. A `401` is **not** an error — it resolves to `{authenticated:false, authRequired:true}` (from the body). Other non-2xx throws `ApiError`. |
| `api.login(username, password)` | `POST /api/auth/login` (JSON body) | `Session` on `200`; throws `ApiError` (message = server `error`, e.g. "invalid credentials") on `401`. |
| `api.logout()` | `POST /api/auth/logout` | resolves (best-effort; ignores transport failure). |

```ts
interface Session { authenticated: boolean; authRequired: boolean; username?: string }
```

## View state machine (`+layout.svelte`)

```
loading ──me()──▶ signin      (authRequired && !authenticated)
                └▶ project     (open mode, or already authenticated)

signin ──login() ok──▶ project        (Login advances internally after onauthenticated)
project ──ENTER──▶ app
app ──EXIT──▶ project                 (session intact; FR-006)
project ──SIGN OUT──▶ signin          (logout(); only when authRequired; FR-007)
```

- The **app shell + route content mount only in `app`** (FR-004) — no runs/stream calls before entry.
- On `me()` transport failure: fall back to `project` (open-mode assumption) — no hard lock (Edge Cases).

## Component props

- **Login**: `requireSignIn: boolean` (start at the auth step vs the repo step), `operator: string`,
  `onauthenticated(username)`, `oncomplete(repo)`, `onsignout()`. Identity row + sign-out shown only when a
  real session exists.
- **Sidebar**: `operator: string` (signed-in username, default neutral `operator`), `onexit()` (→ project).
