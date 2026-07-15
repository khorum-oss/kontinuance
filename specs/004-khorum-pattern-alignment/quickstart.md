# Quickstart: Validate Khorum Pattern Alignment

Run these from the repo root after implementation to prove each story end-to-end. All commands must
succeed with the build green (Constitution Principle III).

## Prerequisites

- JDK 21 toolchain; the Gradle wrapper (`./gradlew`).
- Network access to the configured repositories (public set works with `-Pdependency.env=public`).

## 1. Quality gates measure the real code (US1 — P1)

```bash
# Full build with the public dependency environment: compile + KSP + detekt + Kover verify + tests
./gradlew build -Pdependency.env=public

# Aggregated coverage report exists at the ROOT and includes engine
./gradlew koverXmlReport -Pdependency.env=public
test -f build/reports/kover/report.xml && grep -q 'name="org/khorum/oss/kontinuance/engine' build/reports/kover/report.xml \
  && echo "OK: engine classes present in aggregated coverage"
```
Expected: the report lists `engine` packages. Failing-first proof: temporarily add an untested
branch in an `engine` class, re-run `koverXmlReport`, and confirm the coverage number drops (then
revert). Confirm root `build.gradle.kts` Sonar `sonar.coverage.jacoco.xmlReportPaths` points at
`build/reports/kover/report.xml`.

## 2. Shared detekt config (US2 — P2)

```bash
test -f config/detekt/detekt.yml && echo "OK: shared detekt config present"
./gradlew detekt -Pdependency.env=public   # zero violations across all modules
```
Expected: every module's detekt task uses the shared file (`config.setFrom(...)`) and reports no violations.

## 3. dependency.env switch (US3 — P2)

```bash
./gradlew help -Pdependency.env=public     # resolves via public repos
./gradlew help                             # default selection also resolves (proxy → public fallback)
```
Expected: both succeed. Confirm CI (`.github/workflows/*.yml` or the reusable-workflow invocation)
passes `-Pdependency.env=public`.

## 4. Integration-tests module (US4 — P3)

```bash
./gradlew :integration-tests:test -Pdependency.env=public
```
Expected: the module runs its representative integration test green; its coverage over `:engine`
appears in the root aggregate (re-run step 1's coverage check). Confirm `settings.gradle.kts` lists
`integration-tests` and the module builds no publishable artifact.

## 5. Spec Kit parity (US5 — P3)

```bash
test -d specs/004-khorum-pattern-alignment/checklists && echo "OK: checklists/ present"
grep -q '004-khorum-pattern-alignment/plan.md' CLAUDE.md && echo "OK: agent-context pointer current"
```

## 6. Toolchain & housekeeping (US6 — P3)

```bash
grep -q 'kotlin = "2.3.21"' gradle/libs.versions.toml && echo "kotlin bumped" || echo "kotlin bump DEFERRED (see research.md R1)"
! grep -q 'micronautVersion' gradle.properties && echo "OK: stray micronautVersion removed"
grep -q '.kotlin/\*\*' .gitignore && echo "OK: .kotlin/** ignored"
```
Expected: `micronautVersion` gone, `.kotlin/**` ignored, and either the Kotlin bump applied (build
green on 2.3.21) or explicitly deferred with the blocker recorded.

## 7. Out-of-scope capabilities intact (SC-007)

```bash
grep -q 'khorum.pipeline' build.gradle.kts && test -d docs && test -f docs/roadmap.md \
  && test -d .specify/extensions/git && test -d core-test/src && echo "OK: native strengths preserved"
```

## Full acceptance

`./gradlew build -Pdependency.env=public` green + all seven checks above pass ⇒ feature complete.
Regenerate `gradle/verification-metadata.xml` for any changed dependency and confirm verification
stays enabled.
