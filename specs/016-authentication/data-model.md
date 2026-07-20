# Data Model: Server Authentication & Session

Two in-memory entities; no persistence, no schema.

## OperatorCredentials

The single configured credential pair that gates the API.

| Field | Source | Notes |
|---|---|---|
| `username` | `kontinuance.auth.username` (env `KONTINUANCE_AUTH_USERNAME`) | blank/absent ⇒ not configured |
| `password` | `kontinuance.auth.password` (env `KONTINUANCE_AUTH_PASSWORD`) | blank/absent ⇒ not configured; never logged |
| `enabled` (derived) | — | `true` iff **both** username and password are non-blank |

**Rules**

- `enabled == false` ⇒ open mode: the filter passes all requests through; a startup WARN is logged.
- `matches(user, pass)` returns true **only** when `enabled` and both fields match in **constant time**
  (`MessageDigest.isEqual`), combined so a wrong username and a wrong password are indistinguishable.
- Held only in memory (bound from configuration at startup); the password value is never written to logs or
  responses.

## Session

A server-issued token bound to a signed-in username.

| Field | Type | Notes |
|---|---|---|
| `token` | String | 32 bytes from `SecureRandom`, base64url (no padding); the `KSESSION` cookie value |
| `username` | String | the signed-in operator (echoed by `who-am-I`) |

**Lifecycle**

1. **Create** — at successful `login`: generate `token`, put `token → username` in the store, set the
   `KSESSION` cookie (HttpOnly, SameSite=Lax, Path=/).
2. **Validate** — on each protected request when enforced: read `KSESSION`; the session is valid iff the
   token is present in the store. Unknown/absent/tampered token ⇒ treated as no session ⇒ 401.
3. **Destroy** — at `logout`: remove the token from the store and expire the cookie (`Max-Age=0`). A request
   carrying the removed token is thereafter rejected.

**Constraints**

- In-memory `ConcurrentHashMap<token, username>` — not persisted, not shared across instances; cleared on
  restart (single-instance model). No expiry/TTL in this feature (a future hardening).
- The cookie carries only the opaque token; the username is never placed in a client-readable cookie.
