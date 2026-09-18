#!/usr/bin/env bash
#
# Builds every Flux overlay and validates the rendered objects against real
# schemas, including the CRDs the charts install.
#
# The overlay list is derived from the Flux Kustomization CRs rather than
# written here, so a path CI builds and a path the cluster applies cannot drift.
# A directory that no Kustomization CR points at is reported as uncovered: it is
# either dead or about to be applied by nobody.
#
# Requires: kubectl, kubeconform, python3.
set -euo pipefail

FLUX_ROOT=${FLUX_ROOT:-platform/cluster/flux}
CATALOG=${CATALOG:-https://raw.githubusercontent.com/datreeio/CRDs-catalog/main}

# Kinds whose schema must actually be found. kubeconform skips a resource it has
# no schema for, and a skip is silent, so without this list the CRDs we most
# want checked would pass by not being checked at all.
REQUIRED_KINDS=${REQUIRED_KINDS:-Canary,IngressRoute,Middleware,VaultStaticSecret,HelmRelease,HelmRepository,Kustomization,GitRepository}

command -v kubectl >/dev/null || { echo "::error::kubectl not found"; exit 1; }
command -v kubeconform >/dev/null || { echo "::error::kubeconform not found"; exit 1; }

RENDER_DIR=$(mktemp -d)
trap 'rm -rf "$RENDER_DIR"' EXIT

# -strict rejects unknown fields, which is what turns a misspelled Canary key
# into a failed build rather than a field the cluster silently ignores.
# -verbose because the JSON report otherwise lists only failures, and the
# summary below needs to see which kinds were validated and which were skipped
# for want of a schema.
validate() {
  kubeconform \
    -strict \
    -verbose \
    -ignore-missing-schemas \
    -schema-location default \
    -schema-location "$CATALOG/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json" \
    "$@"
}

# `--self-test` proves the gate can still fail. A validator that has quietly
# stopped validating — a lost schema location, an -ignore flag too many — passes
# every real manifest, so the only way to trust a green run is to keep one
# manifest that must go red. The fixture lives here rather than in the Flux tree,
# where an intentionally invalid Canary would read as a mistake.
if [[ ${1:-} == "--self-test" ]]; then
  cat > "$RENDER_DIR/bad-canary.yaml" <<'EOF'
apiVersion: flagger.app/v1beta1
kind: Canary
metadata:
  name: deliberately-broken
  namespace: default
spec:
  provider: kubernetes
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api
  service:
    port: "8080"
  analysis:
    interval: 30s
    iterations: 4
    thresholdd: 3
EOF
  if validate "$RENDER_DIR/bad-canary.yaml" > "$RENDER_DIR/self-test.log" 2>&1; then
    echo "::error::the validator accepted a Canary with a misspelled key and a string port; it is not validating CRDs"
    cat "$RENDER_DIR/self-test.log"
    exit 1
  fi
  echo "Self-test: a malformed Canary is rejected, as it should be."
  sed 's/^/  /' "$RENDER_DIR/self-test.log"
  exit 0
fi

[[ -d "$FLUX_ROOT" ]] || { echo "::error::$FLUX_ROOT not found; run this from the repository root"; exit 1; }

# 1. The paths Flux actually applies. Written to a file rather than read through
# a process substitution, so a YAML parse error fails the run instead of being
# swallowed into an empty list.
python3 - "$FLUX_ROOT" > "$RENDER_DIR/paths.txt" <<'PY'
import os, posixpath, sys, yaml
root = sys.argv[1]
paths = set()
for dirpath, _, files in os.walk(root):
    for f in files:
        if not f.endswith((".yaml", ".yml")):
            continue
        full = os.path.join(dirpath, f)
        try:
            docs = list(yaml.safe_load_all(open(full)))
        except yaml.YAMLError as e:
            print(f"::error file={full}::unparseable YAML: {e}", file=sys.stderr)
            sys.exit(1)
        for d in docs:
            if not isinstance(d, dict):
                continue
            if d.get("kind") != "Kustomization":
                continue
            if not str(d.get("apiVersion", "")).startswith("kustomize.toolkit.fluxcd.io/"):
                continue
            p = (d.get("spec") or {}).get("path")
            if p:
                # normpath, not lstrip("./"): lstrip removes any leading '.' or
                # '/' character, which would eat the first segment of a path
                # into a dot-named directory.
                paths.add(posixpath.normpath(p))
for p in sorted(paths):
    print(p)
PY

mapfile -t PATHS < "$RENDER_DIR/paths.txt"

if [[ ${#PATHS[@]} -eq 0 ]]; then
  echo "::error::no Flux Kustomization paths found under $FLUX_ROOT"
  exit 1
fi

# 2. Build each one.
echo "Overlays declared by Flux Kustomization CRs: ${#PATHS[@]}"
for overlay in "${PATHS[@]}"; do
  if [[ ! -d "$overlay" ]]; then
    echo "::error::a Kustomization CR points at $overlay, which does not exist"
    exit 1
  fi
  echo "::group::kustomize build $overlay"
  out="$RENDER_DIR/$(echo "$overlay" | tr '/' '_').yaml"
  kubectl kustomize "$overlay" > "$out"
  printf '%s  %s objects\n' "$overlay" "$(grep -c '^kind:' "$out" || true)"
  echo "::endgroup::"
done

# 3. Every buildable app directory must be covered by one of those paths.
UNCOVERED=0
for dir in "$FLUX_ROOT"/apps/*/ "$FLUX_ROOT"/clusters/*/; do
  dir=${dir%/}
  [[ -f "$dir/kustomization.yaml" ]] || continue
  covered=0
  for overlay in "${PATHS[@]}"; do
    [[ "$dir" == "$overlay" ]] && covered=1 && break
  done
  if [[ $covered -eq 0 ]]; then
    echo "::error::$dir builds but no Flux Kustomization CR applies it"
    UNCOVERED=1
  fi
done
[[ $UNCOVERED -eq 0 ]] || exit 1

# 4. Validate the rendered objects.
echo "::group::kubeconform"
set +e
validate -output json "$RENDER_DIR"/*.yaml > "$RENDER_DIR/report.json"
KC=$?
set -e
echo "::endgroup::"

# 5. Report what was checked, and fail on anything invalid or on a required kind
# that had no schema to check against.
python3 - "$RENDER_DIR/report.json" "$REQUIRED_KINDS" "$KC" <<'PY'
import collections, json, sys

report, required_csv, kc = sys.argv[1], sys.argv[2], int(sys.argv[3])
required = {k.strip() for k in required_csv.split(",") if k.strip()}
data = json.load(open(report))

by_status = collections.defaultdict(collections.Counter)
problems = []
for r in data.get("resources", []):
    status = r.get("status", "unknown")
    by_status[status][r.get("kind", "?")] += 1
    if status in ("statusInvalid", "statusError"):
        problems.append(f"{r.get('kind')}/{r.get('name')}: {r.get('msg')}")

validated = by_status["statusValid"]
skipped = by_status["statusSkipped"]

print("validated:")
for kind, n in sorted(validated.items()):
    print(f"  {kind} x{n}")
if skipped:
    print("no schema, not checked:")
    for kind, n in sorted(skipped.items()):
        print(f"  {kind} x{n}")

failed = False
for line in problems:
    print(f"::error::{line}")
    failed = True

missing = required & set(skipped)
if missing:
    print(f"::error::no schema found for required kind(s): {', '.join(sorted(missing))}")
    failed = True

unseen = required - set(validated) - set(skipped)
if unseen:
    print(f"::notice::required kind(s) not present in any overlay: {', '.join(sorted(unseen))}")

if failed or (kc != 0 and not problems):
    sys.exit(1)
PY

echo "Flux manifests build and validate."
