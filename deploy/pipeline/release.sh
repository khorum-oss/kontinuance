#!/usr/bin/env bash
# Build + push the Kontinuance images and pin the STAGE overlay's image tag.
# ArgoCD auto-syncs stage from git, so committing the pinned tag rolls it out.
#
# Usage:  REGISTRY=ghcr.io/khorum-oss [TAG=<tag>] deploy/pipeline/release.sh
#   REGISTRY  target registry/namespace for the images (required)
#   TAG       image tag (default: the current git short SHA)
#
# Requires: docker (logged in to the registry) and kustomize.
set -euo pipefail

usage() { sed -n '2,9p' "$0" >&2; exit "${1:-1}"; }
[ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ] && usage 0
: "${REGISTRY:?set REGISTRY, e.g. ghcr.io/khorum-oss}"

ROOT="$(git rev-parse --show-toplevel)"
TAG="${TAG:-$(git -C "$ROOT" rev-parse --short HEAD)}"

for svc in server web; do
  image="${REGISTRY}/kontinuance-${svc}:${TAG}"
  echo ">> building ${image}"
  docker build -f "${ROOT}/deploy/${svc}.Dockerfile" -t "${image}" "${ROOT}"
  echo ">> pushing ${image}"
  docker push "${image}"
done

echo ">> pinning stage overlay to ${TAG}"
cd "${ROOT}/deploy/k8s/overlays/stage"
kustomize edit set image "kontinuance-server=${REGISTRY}/kontinuance-server:${TAG}"
kustomize edit set image "kontinuance-web=${REGISTRY}/kontinuance-web:${TAG}"

cat <<EOF
Done. Commit the change to roll stage:
  git add deploy/k8s/overlays/stage/kustomization.yaml
  git commit -m "release: stage -> ${TAG}"
  git push        # ArgoCD auto-syncs kontinuance-stage
EOF
