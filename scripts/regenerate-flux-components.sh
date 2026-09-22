#!/usr/bin/env bash
#
# Writes gotk-components.yaml from the flux CLI, and with --check proves the
# committed file is exactly what the CLI emits.
#
# The file carries a DO NOT EDIT banner it cannot enforce. Something reformatted
# it after generation once already, which turned a four-image version bump into
# an 11,000 line diff with six real changes hidden in it. The version and the
# component list live here rather than in a shell history, so an upgrade is a
# one-line edit and its diff is only content.
#
#   regenerate-flux-components.sh            rewrite the file
#   regenerate-flux-components.sh --check    fail if the file has drifted
#   regenerate-flux-components.sh --self-test
#   regenerate-flux-components.sh --print-version
set -euo pipefail

# The cluster runs what this CLI emits, so the version is a deployment decision
# and belongs in the file, not in whatever happens to be on someone's PATH.
FLUX_VERSION=${FLUX_VERSION:-2.8.8}
COMPONENTS_EXTRA=image-reflector-controller,image-automation-controller
TARGET=${TARGET:-platform/cluster/flux/clusters/production/flux-system/gotk-components.yaml}

# Byte-for-byte, deliberately. A YAML-aware comparison would accept the
# reformatting this exists to catch.
compare() {
  local committed=$1 generated=$2
  if diff -u "$committed" "$generated" > /dev/null 2>&1; then
    echo same
  else
    echo drifted
  fi
}

self_test() {
  local dir got
  dir=$(mktemp -d)
  trap 'rm -rf "$dir"' RETURN

  printf 'a: 1\nb:\n  - x\n' > "$dir/committed.yaml"
  cp "$dir/committed.yaml" "$dir/identical.yaml"
  # The exact change that hid in the last diff: same YAML, reindented list.
  printf 'a: 1\nb:\n- x\n' > "$dir/reformatted.yaml"

  got=$(compare "$dir/committed.yaml" "$dir/identical.yaml")
  [ "$got" = same ] || { echo "self-test FAILED: identical files reported $got"; return 1; }

  got=$(compare "$dir/committed.yaml" "$dir/reformatted.yaml")
  [ "$got" = drifted ] || {
    echo "self-test FAILED: a reindented copy reported $got, so the check is not byte-for-byte"
    return 1
  }
  echo "self-test ok: a reindented copy of the same YAML counts as drift"
}

[ "${1:-}" = "--self-test" ] && { self_test; exit $?; }

# So CI installs the version this generates with, rather than repeating it in a
# workflow where the two could drift apart.
[ "${1:-}" = "--print-version" ] && { echo "$FLUX_VERSION"; exit 0; }

command -v flux >/dev/null || {
  echo "::error::flux not found; install v$FLUX_VERSION from https://github.com/fluxcd/flux2/releases"
  exit 1
}

# A different CLI emits a different file, so a mismatch here would look like
# drift in the committed file rather than the wrong tool on the PATH.
have=$(flux --version | awk '{print $NF}')
[ "$have" = "$FLUX_VERSION" ] || {
  echo "::error::flux $have is on the PATH, this repository generates with v$FLUX_VERSION"
  exit 1
}

GENERATED=$(mktemp)
trap 'rm -f "$GENERATED" "$GENERATED.diff"' EXIT
flux install --export --components-extra="$COMPONENTS_EXTRA" > "$GENERATED"

if [ "${1:-}" = "--check" ]; then
  if [ "$(compare "$TARGET" "$GENERATED")" = same ]; then
    echo "$TARGET is what flux v$FLUX_VERSION emits."
    exit 0
  fi
  echo "::error::$TARGET is not what flux v$FLUX_VERSION emits; it was hand-edited or reformatted"
  # Written whole and then trimmed: `diff | head` closes the pipe early, and
  # under pipefail that leaves 141 rather than the 1 this means to exit with.
  diff -u "$TARGET" "$GENERATED" > "$GENERATED.diff" || true
  head -40 "$GENERATED.diff"
  echo "Run scripts/regenerate-flux-components.sh to restore it."
  exit 1
fi

cp "$GENERATED" "$TARGET"
echo "$TARGET written from flux v$FLUX_VERSION with $COMPONENTS_EXTRA."
