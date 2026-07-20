# Research: Server Authentication & Session

No open `NEEDS CLARIFICATION` — the user input fixed the approach. The decisions below record *why* each
choice satisfies the spec and the constitution.

## R1 — Auth mechanism: reactive `WebFilter` + session cookie, not Spring Security

- **Decision**: Implement a plain `org.springframework.web.server.WebFilter` at highest precedence plus a
  small in-memory session store and an `@RestController`. Do **not** add Spring Security.
- **Rationale**: Principle V forbids widening the verification trust set without cause. `spring-boot-
  starter-security` is not in the version catalog and would pull a new group of artifacts requiring
  verification-metadata entries. A `WebFilter` is the framework's own extension point and needs nothing
  beyond `spring-boot-starter-webflux`, which is already a dependency. The feature is a single credential
  pair — the full Security machinery is disproportionate.
- **Alternatives considered**: (a) Spring Security WebFlux — rejected: new dependency + verification churn.
  (b) HTTP Basic at the reverse proxy — already documented as the interim mitigation; the point of this
  feature is an in-app gate that the UI can drive (login/logout/identity), which a proxy cannot expose to
  the SPA.

## R2 — Enforcement toggle: enabled iff both username and password are configured

- **Decision**: `AuthCredentials.enabled = username.isNotBlank() && password.isNotBlank()`. When disabled,
  the filter is a pass-through and a WARN is logged once at startup.
- **Rationale**: FR-001/FR-003 and US2 require that the current loopback deployment and the existing
  `@SpringBootTest` suite keep working with zero configuration. Gating on *configured credentials* makes
  enforcement opt-in and keeps open mode the default. Treating "only one of the two set" as disabled (not a
  half-enabled error) avoids a confusing partial state (spec Edge Cases).
- **Alternatives considered**: a separate `kontinuance.auth.enabled` boolean — rejected as redundant and a
  foot-gun (enabled=true with no password). Presence of credentials is the single source of truth.

## R3 — Credential comparison: constant time via `MessageDigest.isEqual`

- **Decision**: Compare the supplied username and password to the configured values with
  `java.security.MessageDigest.isEqual(a.toByteArray(), b.toByteArray())`, AND the two results together.
- **Rationale**: FR-008 requires constant-time comparison so timing does not reveal how many leading
  characters matched. `MessageDigest.isEqual` is documented as time-constant in modern JDKs and is in the
  JDK (no dependency). Comparing both fields and combining with a non-short-circuiting `and` keeps
  wrong-username and wrong-password indistinguishable (spec Edge Cases / SC-004).
- **Alternatives considered**: `String.equals` — rejected (short-circuits, leaks a timing signal).
  Hashing the stored password (bcrypt/argon2) — out of scope (no new dependency, single operator secret
  held only in memory); noted as a future hardening.

## R4 — Session token + cookie

- **Decision**: On successful login, generate a 32-byte `SecureRandom` value, base64url-encode it (no
  padding), store `token → username` in a `ConcurrentHashMap`, and set it as cookie `KSESSION` with
  `HttpOnly`, `SameSite=Lax`, `Path=/`. Logout removes the entry and expires the cookie (`Max-Age=0`).
- **Rationale**: FR-004/FR-009 require an HttpOnly session cookie returned on same-origin calls. 32 bytes of
  CSPRNG output is an unguessable opaque token; base64url keeps it header-safe. `SameSite=Lax` fits the
  same-origin SPA (spec Assumptions) and blocks cross-site sending. `Secure` is intentionally **not** hard-
  coded on the cookie because the default deployment is loopback/HTTP behind a TLS-terminating proxy;
  transport security stays the operator's responsibility (spec Assumptions). In-memory storage matches the
  single-instance, no-cross-restart model already documented for runs.
- **Alternatives considered**: a signed stateless token (JWT-style) — rejected: needs a signing library or
  hand-rolled crypto and gives no benefit for a single instance; a server-side map supports real logout
  (revocation), which a stateless token cannot without a denylist.

## R5 — Public paths and the filter's placement

- **Decision**: The filter runs at `Ordered.HIGHEST_PRECEDENCE`. Public prefixes (always allowed):
  `/api/auth/`, exact `/api/health`, and `/actuator/`. Everything else — including `/api/runs/**`,
  `/api/runs/stream` (SSE), and `/ws/runs` (WebSocket upgrade) — requires a valid `KSESSION` when enforced.
- **Rationale**: FR-007. A WebFilter executes before handler mapping for *all* exchanges, so the same gate
  covers the annotated controllers, the SSE flow, and the WebSocket upgrade request uniformly — no
  per-controller wiring. Auth endpoints must be public or no one could sign in; health/actuator-health must
  be public for uptime checks in both modes.
- **Alternatives considered**: per-controller checks or method annotations — rejected: easy to forget on a
  new endpoint and would miss the WebSocket upgrade, which is not an annotated handler.

## R6 — Request/response encoding: reuse the raw-`ByteArray` JSON pattern

- **Decision**: The `AuthController` reads the login body as `@RequestBody String` and parses it with
  `kotlinx.serialization.json.Json`; responses are pre-serialized JSON written as `ResponseEntity<ByteArray>`
  with `application/json`, exactly like `RunController`/`TriggerController`. The filter's 401 is written
  directly to `ServerHttpResponse` as JSON bytes.
- **Rationale**: WebFlux has no `String → application/json` writer and the codebase deliberately avoids
  `jackson-module-kotlin` (not trusted/registered). Reusing kotlinx-serialization keeps the no-new-dependency
  constraint (FR-010) and matches the established controller style (SC-006).
- **Alternatives considered**: `@RequestBody LoginRequest` via Jackson — rejected: Kotlin data-class binding
  needs `jackson-module-kotlin` (a dependency/verification change).
