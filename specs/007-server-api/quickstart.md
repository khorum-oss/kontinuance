# Quickstart: Server / Read API

## Run it
```bash
./gradlew :server:install                 # installs `kontinuance-api` to ~/.local/bin
kontinuance-api                           # binds 127.0.0.1:8077, serves ~/.kontinuance/runs
# or: kontinuance-api --host 0.0.0.0 --port 9000 --store /path/to/runs
```

## Validate (US1–US3)
```bash
curl -s localhost:8077/api/health                 # {"status":"ok"}
curl -s "localhost:8077/api/runs?limit=20"        # {"runs":[ ...newest first... ]}
curl -s localhost:8077/api/runs/<id>              # a single run, or 404 {"error":"not found"}
curl -s -o /dev/null -w "%{http_code}\n" localhost:8077/api/runs/nope   # 404
curl -s -o /dev/null -w "%{http_code}\n" localhost:8077/nope            # 404 unknown route
```
After the `kontinuance-ci` service records runs (006), they appear here — same store, no discrepancy (SC-004).

## Automated verification
`./gradlew :server:test` — handler unit tests (list/detail/health, limit bounding, unknown id) and a
real `HttpClient` round-trip against a started `HttpApiServer` over a temp store. Full `./gradlew build`
stays green; no new dependency.
