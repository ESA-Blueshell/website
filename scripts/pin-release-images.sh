#!/usr/bin/env bash
#
# The version tag is written once, at release, from the digest the overlay
# pinned. Everything before that resolves images by their immutable sha tag.
#
#   pin-release-images.sh write  v1.9.0 sha-abc1234    pin the overlay
#   pin-release-images.sh check  v1.9.0               verify, exit 1 on drift
#   pin-release-images.sh publish v1.9.0               tag the pinned digests
#   pin-release-images.sh --self-test
set -euo pipefail

REGISTRY=ghcr.io/esa-blueshell
STATELESS=platform/cluster/flux/apps/stateless/kustomization.yaml
MAIL=platform/cluster/flux/apps/mail/kustomization.yaml
SERVICES=(api frontend stalwart-tools)

overlay_for() {
  case $1 in
    stalwart-tools) echo "${MAIL_OVERLAY:-$MAIL}" ;;
    *) echo "${OVERLAY:-$STATELESS}" ;;
  esac
}

digest_of() {
  docker buildx imagetools inspect "$REGISTRY/$1:$2" --format '{{json .Manifest}}' 2>/dev/null \
    | jq -er .digest
}

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
s, n = entry.subn(
    f"  - name: ghcr.io/esa-blueshell/{service}\n    newTag: {tag}\n    digest: {digest}\n",
    s, count=1)
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
        print(f"{i.get('newTag') or '-'} {i.get('digest') or '-'}")
        break
PY
}

self_test() {
  local tmp; tmp=$(mktemp -d); trap 'rm -rf "$tmp"' RETURN
  printf 'images:\n  - name: ghcr.io/esa-blueshell/api\n    newTag: v0.0.1\n  - name: ghcr.io/esa-blueshell/frontend\n    newTag: v0.0.1\n' > "$tmp/k.yaml"
  pin "$tmp/k.yaml" api v1.2.3 sha256:abc
  [ "$(pinned "$tmp/k.yaml" api)" = "v1.2.3 sha256:abc" ] \
    || { echo "self-test FAILED: wrote '$(pinned "$tmp/k.yaml" api)'"; return 1; }
  [ "$(pinned "$tmp/k.yaml" frontend)" = "v0.0.1 -" ] \
    || { echo "self-test FAILED: touched the other entry"; return 1; }
  echo "self-test ok: writes one entry and reads it back"
}

[ "${1:-}" = "--self-test" ] && { self_test; exit $?; }

MODE=${1:?usage: pin-release-images.sh write|check|publish <version> [sha-tag]}
VERSION=${2:?a version tag, e.g. v1.9.0}
rc=0

for service in "${SERVICES[@]}"; do
  case "$MODE" in
    write)
      # Pinning commits to the branch and so changes the sha. Resolving a sha
      # again would pin a later build, so a version is pinned once.
      from=${3:?a sha tag to resolve, e.g. sha-abc1234}
      read -r have_tag have_digest <<<"$(pinned "$(overlay_for "$service")" "$service")"
      if [ "$have_tag" = "$VERSION" ] && [ "$have_digest" != "-" ]; then
        echo "$service already pinned $VERSION $have_digest"
        continue
      fi
      digest=$(digest_of "$service" "$from") \
        || { echo "::error::no $service image for $from"; exit 1; }
      pin "$(overlay_for "$service")" "$service" "$VERSION" "$digest"
      echo "$service $VERSION $digest"
      ;;
    check)
      # This version's digest, and it has to exist. Which build produced it is
      # not checked: any build of this version is this version.
      read -r have_tag have_digest <<<"$(pinned "$(overlay_for "$service")" "$service")"
      if [ "$have_tag" != "$VERSION" ]; then
        echo "::error::$service is pinned '$have_tag', the release is $VERSION"
        rc=1
      elif [ "$have_digest" = "-" ]; then
        echo "::error::$service is pinned $VERSION with no digest"
        rc=1
      elif ! docker buildx imagetools inspect "$REGISTRY/$service@$have_digest" >/dev/null 2>&1; then
        echo "::error::$service is pinned $have_digest, which is not in the registry"
        rc=1
      else
        echo "$service $VERSION $have_digest"
      fi
      ;;
    publish)
      # The tag names the image the overlay pinned, so it is written once and
      # never points anywhere else.
      read -r _ digest <<<"$(pinned "$(overlay_for "$service")" "$service")"
      [ "$digest" != "-" ] || { echo "::error::$service has no pinned digest to publish"; exit 1; }
      if docker buildx imagetools inspect "$REGISTRY/$service:$VERSION" >/dev/null 2>&1; then
        echo "::error::$REGISTRY/$service:$VERSION already exists; a release tag is written once"
        rc=1
      else
        docker buildx imagetools create -t "$REGISTRY/$service:$VERSION" "$REGISTRY/$service@$digest"
        echo "$service $VERSION $digest"
      fi
      ;;
    *) echo "::error::unknown mode $MODE"; exit 1 ;;
  esac
done
exit $rc
