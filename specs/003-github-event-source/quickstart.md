# Quickstart: GitHub Event Source

How this feature is configured and validated. Written to be runnable **against WireMock**
(no real GitHub) per Constitution II, and to show the real Hestia usage.

## 1. Configure a repository binding

`application.yml` (values via env; the token is env-only, never committed):

```yaml
kontinuance:
  github:
    token: ${GITHUB_TOKEN}            # PAT with repo:status + PR read (App key later for Checks)
    poll-interval: 45s
    repositories:
      - owner: khorum-oss
        repo: relikquary
        pr-pipeline: ci               # descriptor id/path run on PRs
        push-pipeline: deliver        # descriptor run on push to a tracked branch
        tracked-branches: [main]
        check-context: kontinuance/ci # STABLE — the branch-protection required check matches this
```

## 2. Local validation (WireMock — the integration test)

```
./gradlew :github:test        # unit + integration (WireMock stands in for api.github.com)
```

The `EventSourceIT` proves the full loop with **no network**:
1. WireMock serves one open PR (head SHA `abc`) for `khorum-oss/relikquary`.
2. The poller emits a `TriggerEvent(PR, sha=abc)`; the reporter `POST`s a **pending** status
   with context `kontinuance/ci`.
3. The engine runs the `ci` pipeline (a trivial passing descriptor in the test).
4. The reporter `POST`s a **success** status on `abc`; the cursor records `abc`.
5. Re-poll (WireMock unchanged) ⇒ **no** new event, **no** duplicate status (idempotency).
6. A failing-step descriptor ⇒ **failure** status naming the step; a WireMock `403`
   rate-limit ⇒ backoff, no hot-loop, no lost report.

## 3. Turn it into a gate (in GitHub, one-time)

Branch protection on `main` → **Require status checks to pass** → add `kontinuance/ci`.
Now a PR cannot merge until Kontinuance reports success — the "trigger on PR and wait for it
to finish" behavior, with the LAN never exposed.

## 4. Hestia usage (the payoff)

- `pr-pipeline: ci` → runs relikquary's build/test on each PR, gates merge.
- `push-pipeline: deliver` on `main` → the Hestia delivery pipeline (build → push to
  Relikquary registry → render → argocd sync stage → UAT → prod approval), authored as a
  Kontinuance pipeline using the `002` typed steps (`gradleStep`/`dockerStep`) plus the
  Hestia steps (render via zosn, deploy via logos, UAT via euri).

## Optional: webhook mode (later)

Set `kontinuance.github.webhook.enabled: true` + a shared secret, expose the receiver via a
**Cloudflare Tunnel** (no open ports). Signature-verified deliveries feed the *same*
`TriggerEvent` path — polling can stay on as a safety net.
