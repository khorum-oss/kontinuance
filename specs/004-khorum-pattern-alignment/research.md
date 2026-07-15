# Phase 0 Research: Khorum Pattern Alignment

Decisions that resolve the open questions in the Technical Context. Each: **Decision / Rationale /
Alternatives**. The concrete reference is the `khorum-oss/relikquary` checkout.

## R1 — Kotlin `2.1.20` → `2.3.21` bump vs. KSP + Konstellation coupling

**Decision**: Treat the Kotlin bump as **conditional and build-verified**. Bump `kotlin` and the KSP
pin *together* (KSP is versioned as `<kotlin>-<ksp>`, so `2.1.20-1.0.32` must become a `2.3.21-*`
release) and rebuild `:engine` (which runs the Konstellation KSP processor). The bump is "done" only
if `./gradlew build` — including KSP code generation and all tests — passes on `2.3.21`. If no KSP
release matches `2.3.21`, or Konstellation (`meta-dsl 1.0.15` / `dsl 2.0.14`, a khorum-internal lib)
fails to generate/compile against it, **defer the bump**, leave Kotlin at `2.1.20`, and record the
blocker in this file and `docs/roadmap.md`. This matches the spec's stated assumption.

**Rationale**: relikquary reaches `2.3.21` cleanly because it uses **no KSP and no Konstellation**;
kontinuance's generated DSL binds Kotlin↔KSP↔Konstellation tightly, so the version is not freely
movable. Forcing it would risk generation breakage that surfaces in consumers (Principle IV).

**Alternatives**: (a) Bump Kotlin only, leave KSP — rejected: KSP must match the Kotlin compiler
version exactly. (b) Skip the bump entirely — rejected as the default; attempt it first, defer only
on proven incompatibility.

## R2 — `dependency.env` switch shape for kontinuance

**Decision**: Mirror relikquary's mechanism — a single `dependency.env` gradle property gating
repository declarations in both `settings.gradle.kts` (`pluginManagement`) and root
`build.gradle.kts`, with `proxy.location` for the internal path — but adapt the **public repo set** to
kontinuance's needs. relikquary's public set is just `mavenCentral()`/`gradlePluginPortal()`;
kontinuance additionally requires `google()`, the JetBrains IntelliJ repo, and critically the
`open-reliquary` DigitalOcean Spaces CDN (source of the Konstellation + `khorum.*` artifacts). So:
- `dependency.env=public` → `gradlePluginPortal()` + `mavenCentral()` + `google()` + JetBrains repo +
  the `open-reliquary` CDN (the union of what `sharedRepositories()` declares today, minus
  `mavenLocal()`).
- default (`stage`, non-public) → route through `proxy.location`, falling back to the same public set
  so a developer without the internal proxy is **not** broken (a deliberate softening of relikquary's
  stricter "empty unless public" stance, because kontinuance has no guaranteed internal proxy yet).
- CI passes `-Pdependency.env=public`.

**Rationale**: Introduces the org convention and the CI seam **without regressing** today's working
`./gradlew build` (FR-013, and the spec assumption that the default keeps resolving through existing
repos). The stricter proxy-only default can be tightened later when/if kontinuance gets a real
internal proxy.

**Alternatives**: Copy relikquary's "empty repos unless public" verbatim — rejected: it would break
every local build that doesn't run the proxy, a real regression for kontinuance today.

## R3 — Coverage aggregation approach

**Decision**: Replace the hand-rolled root `koverMergedReport` (which `dependsOn` only
`:dsl:koverXmlReport`) and the Sonar path (which reads `:dsl`'s report) with Kover's **project
aggregation**: in the root build, apply the Kover plugin and declare
`kover(project(":engine"))` and `kover(project(":dsl"))` (and, once it exists,
`kover(project(":integration-tests"))`), then point Sonar at the **root** aggregated report
(`build/reports/kover/report.xml`) — exactly relikquary's model
(`kover(project(":backend"))` + `kover(project(":integration-tests"))`, Sonar → root report).
`core-test` is a test-support module and is excluded from aggregation (it ships no production code
that the gate should measure). The per-module `@ExcludeFromCoverage` filters are kept.

**Rationale**: Aggregation is the supported Kover mechanism and is what relikquary uses; it makes the
gate measure `engine`. The bespoke `koverMergedReport` task is the source of the bug.

**Alternatives**: Keep `koverMergedReport` but repoint it at `:engine` — rejected: fragile, and it
would still miss multi-module aggregation once `integration-tests` contributes coverage over `:engine`.

## R4 — Shared detekt configuration content

**Decision**: Add `config/detekt/detekt.yml` layered on defaults (`buildUponDefaultConfig = true`),
seeded from relikquary's deviations (`style.MaxLineLength.maxLineLength: 140`,
`style.ReturnCount.active: false`) since kontinuance's code was written to compatible norms. Each
module's `detekt {}` block adds
`config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))` alongside the
existing `buildUponDefaultConfig = true`. kontinuance's extra per-module report-format + `jvmTarget`
wiring is retained (relikquary simply omits it; keeping it is not a divergence to fix). detekt MUST
stay at zero violations; if the shared config surfaces new findings, fix the code or record the
deviation in the shared file — never baseline (Principle III).

**Rationale**: Single source of truth for lint rules, matching relikquary, with the least chance of
introducing new violations by starting from the same deviations.

**Alternatives**: Author a stricter config from scratch — rejected: risks new violations and scope creep.

## R5 — `integration-tests` module shape

**Decision**: Add a top-level `integration-tests` Gradle module registered via
`includeModules("core-test", "dsl", "engine", "integration-tests")`. It ships **no runnable/publishable
artifact**, depends on `:engine` (the system under test) and the JUnit platform + `:core-test`, and
its coverage over `:engine` is aggregated at the root (R3). It seeds with **one** representative
integration test that drives the built `engine` CLI/`PipelineEngine` end-to-end (proving the module
runs and contributes coverage). Because the v0 engine has **no Spring** yet, this module does not pull
Spring/Testcontainers now; the constitution's `@SpringBootTest`+Testcontainers mandate applies when a
real external integration lands (003+), and this module is its designated home.

**Rationale**: Establishes relikquary's IT-isolation convention and the coverage seam without
importing Spring prematurely into a Spring-less engine.

**Alternatives**: Defer the module until a Spring integration exists — rejected: the user selected it,
and standing it up now (with a real test) is cheap and proves the aggregation wiring.

## R6 — Logging artifact alignment

**Decision**: **Defer/skip** switching `io.github.microutils:kotlin-logging` (`4.0.0-beta-2`) →
relikquary's `io.github.oshai:kotlin-logging-jvm` (`7.0.3`) in this feature. It is a transitive
concern touching source imports and is not part of the approved "everything portable" list the user
enumerated. Record it as a possible future tidy.

**Rationale**: Keep this feature scoped to the enumerated items; a logging-artifact swap is a separate,
source-touching change with its own verification surface.

**Alternatives**: Bundle it in — rejected: out of the agreed scope; would broaden the diff and risk.

## Cross-cutting: verification metadata (Principle V)

Any dependency the above adds or bumps (Kotlin/KSP, any integration-test dep) MUST be reflected in
`gradle/verification-metadata.xml` via
`./gradlew --write-verification-metadata sha256,pgp build` with verification kept enabled. This is a
task in every affected story, not an afterthought.
