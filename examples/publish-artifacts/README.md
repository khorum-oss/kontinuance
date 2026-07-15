# Publish artifacts with Kontinuance

A ready-to-run [Kontinuance](../../docs/cli.md) pipeline that **builds and publishes Maven artifacts
to a repository you choose** — your own private Nexus/Artifactory/GitHub Packages/S3-backed Maven, or
a local `file://` repository for a dry run. You drive it from the installed `kontinuance` CLI; the
repository URL and credentials are supplied as environment secrets, never written into the descriptor.

## Provenance

`publish-artifacts.yaml` is authored **from scratch in Kontinuance's own descriptor schema**
(`pipeline / stages / steps / run / secrets`). It is **not** copied, derived, or mimicked from a
GitHub Actions workflow, from the `hestia-systems` delivery descriptors, or from any external GitHub
YAML — there are no `on:`, `jobs:`, or `uses:` constructs, and nothing here is coupled to GitHub YAML.

## Files

| File | What it is |
|---|---|
| `publish-artifacts.yaml` | The pipeline: a `build` stage then a `publish` stage. **This is what you adapt.** |
| `sample-lib/` | A tiny standalone Gradle project (core `java-library` + `maven-publish`, no external plugins) so the example runs out of the box. Replace with your own project in practice. |

## What it needs (environment secrets)

The engine runs each step in an isolated environment, so **every value is passed in by name as a
secret** (resolved from your environment, masked in logs). Export these before running:

| Variable | Meaning |
|---|---|
| `PROJECT_DIR` | Absolute path to the Gradle project to build + publish (has a `publish` task). |
| `PUBLISH_REPO_URL` | Target Maven repository URL — `https://…` or `file:///abs/path`. |
| `PUBLISH_REPO_USER` | Repository username / token id. Leave empty (`export PUBLISH_REPO_USER=`) for a `file://` repo. |
| `PUBLISH_REPO_PASSWORD` | Repository password / token. Leave empty for a `file://` repo. |

> All four names are declared in the descriptor's `secrets:` lists. A name that is **unset** fails the
> run fast **before** any upload; set it to an empty string when a `file://` repo doesn't need it.

## Run it

```bash
# 1. Install the CLI once (from the repo root):  ./gradlew :engine:install   (puts `kontinuance` on ~/.local/bin)

# 2. Point it at the bundled sample and a throwaway local repo:
export PROJECT_DIR="$(pwd)/sample-lib"
export PUBLISH_REPO_URL="file:///tmp/my-maven-repo"
export PUBLISH_REPO_USER=          # empty: file:// needs no credentials
export PUBLISH_REPO_PASSWORD=

# 3. Validate without running anything (safe):
kontinuance --check publish-artifacts.yaml

# 4. Build + publish:
kontinuance publish-artifacts.yaml

# 5. See what landed:
find /tmp/my-maven-repo -type f
#   → com/example/kontinuance/sample-lib/0.1.0/sample-lib-0.1.0.jar (+ -sources.jar, .pom, .module,
#     maven-metadata.xml, and md5/sha1/sha256/sha512 checksums)
```

## Point it at your own private repository

Publish your real project instead of the sample — **no Kontinuance source changes required**, only
the environment and (optionally) the descriptor:

1. Set `PROJECT_DIR` to your project (it must expose a Gradle `publish` task and a repository that
   reads its URL/credentials from the environment — see `sample-lib/build.gradle.kts` for the pattern).
2. Set `PUBLISH_REPO_URL` + `PUBLISH_REPO_USER` + `PUBLISH_REPO_PASSWORD` for your repository:

   | Repository | `PUBLISH_REPO_URL` | Credentials |
   |---|---|---|
   | **Nexus / Artifactory** | the hosted repo URL (e.g. `https://nexus.example.com/repository/maven-releases/`) | deploy user + password/token |
   | **GitHub Packages** | `https://maven.pkg.github.com/OWNER/REPO` | your username + a PAT with `write:packages` |
   | **S3-backed Maven** | `s3://your-bucket/maven` (your build supplies the S3 auth) | per your S3 setup |
   | **Local dry run** | `file:///abs/path` | leave empty |

3. If your project's wrapper task differs, edit the `run:` commands in `publish-artifacts.yaml`
   (e.g. a specific `publishAllPublicationsToXRepository` or a module path). Keep everything in native
   Kontinuance schema.

## Notes

- The step commands use `./gradlew` (the usual project convention). `sample-lib/gradlew` is a small,
  clearly-labeled shim that delegates to a Gradle already on your `PATH` so the sample runs without
  downloading a distribution; a real project has a proper Gradle wrapper there instead.
- Signing (GPG) for repositories that require it is supplied the same way — as environment secrets
  your project's build reads; add the relevant secret names to the `publish` step's `secrets:` list.
- This example changes nothing in the engine; it is descriptor + docs on top of the existing CLI.
