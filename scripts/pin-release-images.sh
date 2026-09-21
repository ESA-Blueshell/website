#!/usr/bin/env bash
#
# Writes the api and frontend digests into the stateless overlay, or checks the
# ones already there. The writer and the checker share this file so they cannot
# disagree about what the pin should be.
#
#   pin-release-images.sh v1.8.0            write
#   pin-release-images.sh v1.8.0 --check    compare, exit 1 on a mismatch
#   pin-release-images.sh --self-test       prove the check can fail
set -euo pipefail

OVERLAY=${OVERLAY:-platform/cluster/flux/apps/stateless/kustomization.yaml}
REGISTRY=ghcr.io/esa-blueshell
SERVICES=(api frontend)

digest_of() {
  docker buildx imagetools inspect "$REGISTRY/$1:$2" --format '{{json .Manifest}}' 2>/dev/null \
    | jq -er .digest
}

# Rewrites the newTag and digest lines under one image entry, leaving the rest
# of the file alone.
pin() {
  local file=$1 service=$2 tag=$3 digest=$4
  OVERLAY_FILE=$file SERVICE=$service TAG=$tag DIGEST=$digest python3 - <<'PY'
import os, re
path, service = os.environ['OVERLAY_FILE'], os.environ['SERVICE']
tag, digest = os.environ['TAG'], os.environ['DIGEST']
s = open(path).read()
entry = re.compile(
    rf"(  - name: ghcr\.io/esa-blueshell/{service}\n)"
    rf"(?:    newTag: \S+\n)?(?:    digest: \S+\n)?")
replacement = f"  - name: ghcr.io/esa-blueshell/{service}\n    newTag: {tag}\n    digest: {digest}\n"
s, n = entry.subn(replacement, s, count=1)
if n != 1:
    raise SystemExit(f"no image entry for {service} in {path}")
open(path, 'w').write(s)
PY
}

pinned() {
  OVERLAY_FILE=$1 SERVICE=$2 python3 - <<'PY'
import os, yaml
d = yaml.safe_load(open(os.environ['OVERLAY_FILE']))
for i in d.get('images') or []:
    if i.get('name', '').endswith('/' + os.environ['SERVICE']):
        print(f"{i.get('newTag','')} {i.get('digest','')}")
        break
PY
}

self_test() {
  local tmp; tmp=$(mktemp -d); trap 'rm -rf "$tmp"' RETURN
  cat > "$tmp/k.yaml" <<'EOF'
images:
  - name: ghcr.io/esa-blueshell/api
    newTag: v0.0.1
  - name: ghcr.io/esa-blueshell/frontend
    newTag: v0.0.1
EOF
  pin "$tmp/k.yaml" api v1.2.3 sha256:abc
  local got; got=$(pinned "$tmp/k.yaml" api)
  [ "$got" = "v1.2.3 sha256:abc" ] || { echo "self-test FAILED: wrote '$got'"; return 1; }
  [ "$(pinned "$tmp/k.yaml" frontend)" = "v0.0.1 " ] \
    || { echo "self-test FAILED: touched the other entry"; return 1; }
  echo "self-test ok: writes one entry and reads it back"
}

[ "${1:-}" = "--self-test" ] && { self_test; exit $?; }

TAG=${1:?usage: pin-release-images.sh <tag> [--check]}
MODE=${2:-write}
rc=0

for service in "${SERVICES[@]}"; do
  digest=$(digest_of "$service" "$TAG") \
    || { echo "::error::no $service image for $TAG in the registry"; exit 1; }
  if [ "$MODE" = "--check" ]; then
    read -r have_tag have_digest <<<"$(pinned "$OVERLAY" "$service")"
    if [ "$have_tag" != "$TAG" ] || [ "$have_digest" != "$digest" ]; then
      echo "::error::$service is pinned $have_tag $have_digest, the registry has $TAG $digest"
      rc=1
    else
      echo "$service $TAG $digest"
    fi
  else
    pin "$OVERLAY" "$service" "$TAG" "$digest"
    echo "$service $TAG $digest"
  fi
done
exit $rc
