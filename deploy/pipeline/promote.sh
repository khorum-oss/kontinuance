#!/usr/bin/env bash
# Promote the STAGE-tested image tags to PROD by pinning the prod overlay to whatever stage currently
# runs. Prod is a manual gate: this only edits git — apply it with `argocd app sync kontinuance-prod`.
#
# Usage:  deploy/pipeline/promote.sh
#
# Requires: kustomize.
set -euo pipefail

usage() { sed -n '2,7p' "$0" >&2; exit "${1:-1}"; }
[ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ] && usage 0

ROOT="$(git rev-parse --show-toplevel)"
STAGE_DIR="${ROOT}/deploy/k8s/overlays/stage"
PROD_DIR="${ROOT}/deploy/k8s/overlays/prod"

# Read the fully-resolved image refs stage currently deploys (kustomize resolves newName+newTag).
rendered="$(kustomize build "${STAGE_DIR}")"
server_image="$(printf '%s\n' "${rendered}" | grep -Eo 'image: [^ ]*kontinuance-server:[^ ]+' | head -n1 | awk '{print $2}')"
web_image="$(printf '%s\n' "${rendered}" | grep -Eo 'image: [^ ]*kontinuance-web:[^ ]+' | head -n1 | awk '{print $2}')"

[ -n "${server_image}" ] && [ -n "${web_image}" ] || {
  echo "could not read stage image refs; is the stage overlay pinned (run release.sh)?" >&2
  exit 1
}

echo ">> promoting to prod:"
echo "   ${server_image}"
echo "   ${web_image}"
cd "${PROD_DIR}"
kustomize edit set image "kontinuance-server=${server_image}"
kustomize edit set image "kontinuance-web=${web_image}"

cat <<EOF
Done. Commit, then sync prod deliberately (the production gate):
  git add deploy/k8s/overlays/prod/kustomization.yaml
  git commit -m "promote: prod -> stage-tested images"
  git push
  argocd app sync kontinuance-prod
EOF
