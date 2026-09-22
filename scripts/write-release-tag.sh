#!/usr/bin/env bash
#
# Points a version tag at a digest, once. The digest is passed in by the build
# that produced it rather than resolved here: a `sha-` tag is rewritten by any
# later build of the same commit, and the release merge commit is built twice
# (release.yml calls build.yml while the push to main runs it as well), so
# re-resolving one is a race.
#
#   write-release-tag.sh ghcr.io/esa-blueshell/api v1.9.0 sha256:abc...
#   write-release-tag.sh --self-test
set -euo pipefail

# A released version names one image for good, so an existing tag naming
# something else is a conflict rather than something to overwrite. Re-running a
# release that already wrote its tag is a no-op, not a failure.
tag_decision() {
  local existing=$1 wanted=$2
  # jq prints a bare `null` for a manifest with no digest field, which means
  # nothing is there rather than something that conflicts.
  if [ "$existing" = null ]; then existing=; fi
  if [ -z "$existing" ]; then echo write
  elif [ "$existing" = "$wanted" ]; then echo same
  else echo conflict
  fi
}

digest_of_tag() {
  docker buildx imagetools inspect "$1" --format '{{json .Manifest}}' 2>/dev/null \
    | jq -r .digest 2>/dev/null
}

self_test() {
  local got
  # `|`, because a digest already contains the obvious separator.
  for case in '|sha256:new|write' 'sha256:new|sha256:new|same' \
              'sha256:old|sha256:new|conflict' 'null|sha256:new|write'; do
    IFS='|' read -r existing wanted want <<<"$case"
    got=$(tag_decision "$existing" "$wanted")
    [ "$got" = "$want" ] || {
      echo "self-test FAILED: '$existing' against '$wanted' decided $got, not $want"
      return 1
    }
  done
  echo "self-test ok: a version tag is written once, an absent tag reads as absent"
}

[ "${1:-}" = "--self-test" ] && { self_test; exit $?; }

IMAGE=${1:?usage: write-release-tag.sh <image> <version> <digest>}
VERSION=${2:?a version tag, e.g. v1.9.0}
DIGEST=${3:?the digest to tag, e.g. sha256:abc...}

existing=$(digest_of_tag "$IMAGE:$VERSION") || existing=
case "$(tag_decision "$existing" "$DIGEST")" in
  same)
    echo "$IMAGE:$VERSION already names $DIGEST" ;;
  conflict)
    echo "::error::$IMAGE:$VERSION names $existing, not $DIGEST; a released version names one image"
    exit 1 ;;
  write)
    docker buildx imagetools create -t "$IMAGE:$VERSION" "$IMAGE@$DIGEST"
    echo "$IMAGE:$VERSION $DIGEST" ;;
esac
