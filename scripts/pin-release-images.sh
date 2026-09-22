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
import os, yaml
path, service = os.environ['OVERLAY_FILE'], os.environ['SERVICE']
doc = yaml.safe_load(open(path)) or {}
images = doc.get('images') or []
for entry in images:
    if entry.get('name', '').endswith('/' + service):
        entry['newTag'] = os.environ['TAG']
        entry['digest'] = os.environ['DIGEST']
        break
else:
    raise SystemExit(f"no image entry for {service} in {path}")
# Round-tripping drops comments, so only the images block is rewritten.
text = open(path).read()
body = yaml.safe_dump({'images': images}, sort_keys=False, default_flow_style=False)
start = text.index('images:')
open(path, 'w').write(text[:start] + body)
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
  printf 'images:\n  - name: ghcr.io/esa-blueshell/api\n    newTag: v0.0.1\n  - name: ghcr.io/esa-blueshell/frontend\n    newTag: v0.0.1\n' > "$tmp/stateless.yaml"
  printf 'images:\n  - name: ghcr.io/esa-blueshell/stalwart-tools\n    digest: sha256:old\n' > "$tmp/mail.yaml"

  pin "$tmp/stateless.yaml" api v1.2.3 sha256:abc
  [ "$(pinned "$tmp/stateless.yaml" api)" = "v1.2.3 sha256:abc" ] \
    || { echo "self-test FAILED: wrote '$(pinned "$tmp/stateless.yaml" api)'"; return 1; }
  [ "$(pinned "$tmp/stateless.yaml" frontend)" = "v0.0.1 -" ] \
    || { echo "self-test FAILED: touched the other entry"; return 1; }

  # A digest-only entry is the shape stalwart-tools ships in.
  [ "$(pinned "$tmp/mail.yaml" stalwart-tools)" = "- sha256:old" ] \
    || { echo "self-test FAILED: read a digest-only entry as '$(pinned "$tmp/mail.yaml" stalwart-tools)'"; return 1; }
  pin "$tmp/mail.yaml" stalwart-tools v1.2.3 sha256:new
  [ "$(pinned "$tmp/mail.yaml" stalwart-tools)" = "v1.2.3 sha256:new" ] \
    || { echo "self-test FAILED: mail overlay wrote '$(pinned "$tmp/mail.yaml" stalwart-tools)'"; return 1; }

  # Every service must resolve to an overlay, or a pin is written nowhere.
  local svc
  for svc in "${SERVICES[@]}"; do
    [ -n "$(overlay_for "$svc")" ] \
      || { echo "self-test FAILED: $svc has no overlay"; return 1; }
  done

  # Key order must not matter; a regex here once wrote a duplicate newTag.
  printf 'images:\n  - name: ghcr.io/esa-blueshell/api\n    digest: sha256:zzz\n    newTag: v0.0.1\n' > "$tmp/rev.yaml"
  pin "$tmp/rev.yaml" api v9.9.9 sha256:yyy
  [ "$(grep -c 'newTag' "$tmp/rev.yaml")" = 1 ] \
    || { echo "self-test FAILED: wrote a duplicate newTag"; return 1; }
  [ "$(pinned "$tmp/rev.yaml" api)" = "v9.9.9 sha256:yyy" ] \
    || { echo "self-test FAILED: reversed keys read back '$(pinned "$tmp/rev.yaml" api)'"; return 1; }

  echo "self-test ok: both overlays, digest-only entries, every service mapped, key order ignored"
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
      existing=$(docker buildx imagetools inspect "$REGISTRY/$service:$VERSION" \
        --format '{{json .Manifest}}' 2>/dev/null | jq -r .digest 2>/dev/null)
      if [ "$existing" = "$digest" ]; then
        echo "$service $VERSION already points at $digest"
      elif [ -n "$existing" ]; then
        echo "::error::$REGISTRY/$service:$VERSION is $existing, not the pinned $digest; a release tag is written once"
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
