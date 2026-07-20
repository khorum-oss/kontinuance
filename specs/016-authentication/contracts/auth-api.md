# Contract: Authentication API

All bodies are `application/json`. The session is carried by the **`KSESSION`** cookie (HttpOnly,
SameSite=Lax, Path=/). "Enforced" = both `kontinuance.auth.username` and `kontinuance.auth.password` set;
otherwise "open".

## Public paths (never require a session, both modes)

- `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`
- `GET /api/health`
- `GET /actuator/health`

Everything else under `/api/**` and `/ws/**` requires a valid `KSESSION` **when enforced**. When open, all
paths are allowed.

## `POST /api/auth/login`

Request body:

```json
{ "username": "operator", "password": "secret" }
```

| Case | Status | Response body | Side effect |
|---|---|---|---|
| Enforced, credentials correct | `200` | `{"authenticated":true,"authRequired":true,"username":"operator"}` | `Set-Cookie: KSESSION=…; HttpOnly; SameSite=Lax; Path=/` |
| Enforced, credentials wrong | `401` | `{"authenticated":false,"authRequired":true,"error":"invalid credentials"}` | none |
| Open (no credentials configured) | `200` | `{"authenticated":false,"authRequired":false}` | none (nothing to protect) |
| Malformed / missing body | `400` | `{"error":"malformed request body"}` | none |

## `GET /api/auth/me`

| Case | Status | Response body |
|---|---|---|
| Enforced, valid `KSESSION` | `200` | `{"authenticated":true,"authRequired":true,"username":"operator"}` |
| Enforced, no/invalid session | `401` | `{"authenticated":false,"authRequired":true}` |
| Open | `200` | `{"authenticated":false,"authRequired":false}` |

`me` is a **public** path, so the filter always lets it through; the handler itself decides 200 vs 401 from
the session. This lets a client discover whether it must show a login screen (`authRequired`).

## `POST /api/auth/logout`

Always `200` with `{"authenticated":false}`; if a `KSESSION` was present its session is removed from the
store and the cookie is expired (`Set-Cookie: KSESSION=; Max-Age=0; Path=/`). A subsequent protected request
carrying the old token is rejected (`401`).

## Filter rejection (protected path, enforced, no valid session)

```
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{"error":"authentication required"}
```

## Notes

- Wrong-username and wrong-password both yield the identical `401 invalid credentials`, in constant time.
- The `Secure` cookie attribute is not set by the app (loopback/HTTP-behind-TLS-proxy default); TLS is the
  operator's responsibility. An operator terminating TLS can rely on SameSite=Lax + HttpOnly for the SPA.
