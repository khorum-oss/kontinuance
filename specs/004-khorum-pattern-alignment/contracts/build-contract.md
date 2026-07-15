# Contract: Build & CI Surface — Khorum Pattern Alignment

The consumer of this "contract" is a kontinuance developer and the CI pipeline. It defines the
build-level seams this feature introduces or changes. (No runtime/API contract changes — Principle I
is untouched.)

## 1. `dependency.env` resolution property

| Value | Meaning | Repository set |
|---|---|---|
| *(unset)* / `stage` | Local/internal default | `proxy.location`, falling back to the public set below |
| `public` | Public/CI builds | `gradlePluginPortal()` (plugins) · `mavenCentral()` · `google()` · JetBrains IntelliJ repo · `open-reliquary` DO Spaces CDN |

- Selected via `-Pdependency.env=<value>`; companion `proxy.location=<url>` in `gradle.properties`.
- Gated identically in `settings.gradle.kts` (`pluginManagement { repositories { … } }`) and root
  `build.gradle.kts` repositories.
- **CI MUST pass `-Pdependency.env=public`.**
- Rule: the `open-reliquary` CDN is present in *every* selection (Konstellation / `khorum.*` live there).

## 2. Module list

`settings.gradle.kts` registers, via `includeModules(...)`:
```
core-test        # test-support (excluded from coverage aggregation)
dsl              # DSL scaffolding (aggregated)
engine           # production code (aggregated)   ← must be measured by the gate
integration-tests  # NEW — non-publishable; depends on :engine; aggregated
```

## 3. Coverage aggregation

- Root `build.gradle.kts` applies Kover and declares `kover(project(":engine"))`,
  `kover(project(":dsl"))`, `kover(project(":integration-tests"))`.
- Sonar coverage path → the **root** aggregated report: `build/reports/kover/report.xml`.
- The old `koverMergedReport` task and the `:dsl`-only Sonar path are removed.
- `@ExcludeFromCoverage` (per-module `…<module>.common.ExcludeFromCoverage`) filters are retained.

## 4. detekt configuration

- Single shared file: `config/detekt/detekt.yml` (`buildUponDefaultConfig = true`; deviations:
  `style.MaxLineLength.maxLineLength: 140`, `style.ReturnCount.active: false`).
- Every module: `detekt { buildUponDefaultConfig = true; config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml")) }`.
- Gate: **zero violations** across the build.

## 5. `gradle.properties`

- ADD: `dependency.env=stage`, `proxy.location=<url>`.
- REMOVE: `micronautVersion=4.5.3` (unused).
- Keep: existing jvmargs / parallel / cache / KSP / S3 endpoint entries.

## 6. Toolchain & metadata

- `gradle/libs.versions.toml`: `kotlin` → `2.3.21` **and** matching KSP pin — *conditional on
  research.md R1*; if incompatible, stays `2.1.20` and this line is not applied.
- `gradle/verification-metadata.xml`: regenerated for any changed/added artifact; verification stays enabled.
- `.gitignore`: add `.kotlin/**`.
- `CLAUDE.md`: SPECKIT pointer → `specs/004-khorum-pattern-alignment/plan.md`.

## 7. Invariance (out of scope — MUST remain)

`khorum.*` plugins · KSP/Konstellation generation · geordi/`core-test` · `docs/` tree ·
`docs/roadmap.md` · the git Spec Kit extension. None removed or weakened (FR-012 / SC-007).

## Acceptance

`./gradlew build -Pdependency.env=public` is green (compile + KSP + detekt zero-violations + Kover
verify + all tests), the aggregated coverage report includes `engine`, and Sonar reads the root
report. A default-selection build also resolves successfully.
