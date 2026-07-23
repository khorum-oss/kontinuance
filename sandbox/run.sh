#!/usr/bin/env bash
#
# Fresh, local, end-to-end demo of a Kontinuance pipeline building real code.
#
# It snapshots this sandbox app into a throwaway local git repo, then runs the sandbox pipeline through the
# Kontinuance engine CLI: the engine checks the app out into a fresh, ephemeral workspace and runs its
# Gradle build + test tasks, streaming the real step logs, and removes the workspace when it finishes.
# Nothing persists between runs — every run starts from a clean checkout.
#
# Prerequisites: JDK 21 and Gradle on your PATH (the sandbox app ships no wrapper on purpose, to show the
# `gradle:` step's system-gradle fallback). Run it from anywhere:  sandbox/run.sh
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$HERE/.." && pwd)"

# 1) Package the sandbox app as a self-contained local git repo, so the pipeline clones "your app repo"
#    fresh — independent of this repo's own git state, and working even on uncommitted edits.
APP_REPO="$(mktemp -d)"
trap 'rm -rf "$APP_REPO"' EXIT
cp -R "$HERE/src" "$HERE/build.gradle.kts" "$HERE/settings.gradle.kts" "$APP_REPO/"
git -C "$APP_REPO" init -q
git -C "$APP_REPO" -c user.email=demo@kontinuance.local -c user.name=demo add -A
git -C "$APP_REPO" -c user.email=demo@kontinuance.local -c user.name=demo commit -qm "sandbox snapshot"
export REPO_URL="file://$APP_REPO"

# 2) Ensure the `kontinuance` engine CLI is built (skip if it already is).
CLI="$REPO_ROOT/engine/build/install/kontinuance/bin/kontinuance"
if [ ! -x "$CLI" ]; then
	echo "> building the kontinuance CLI (one-time)…"
	"$REPO_ROOT/gradlew" -q :engine:installDist ${GRADLE_ARGS:-}
fi

# 3) Run the sandbox pipeline through the engine: checkout -> assemble -> self-test, logs streamed live.
echo "> running the sandbox pipeline against $REPO_URL"
echo
exec "$CLI" "$HERE/kontinuance.yml"
