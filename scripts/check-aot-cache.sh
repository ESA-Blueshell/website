#!/usr/bin/env bash
# Boots an api image as far as a refreshed context under -XX:AOTMode=on, with the limits and
# JAVA_TOOL_OPTIONS each pod gets, read from the manifests, so a cache a pod's JVM would ignore,
# or a memory line the image's calculator refuses, fails the build rather than a release
# (architecture ADR-013 and ADR-015). The keys are drawn here for the same reason the training
# run draws its own.
set -euo pipefail

image=${1:?usage: check-aot-cache.sh IMAGE}
manifests=platform/cluster/flux/apps/stateless/api
log=$(mktemp)
trap 'rm -f "$log"' EXIT

key() { head -c "$1" /dev/urandom | base64 | tr -d '\n'; }

# "docker-flags|JAVA_TOOL_OPTIONS" for the api container of each manifest: the Deployment, then
# the migrate Job.
shapes=$(python3 - "$manifests/deployment.yaml" "$manifests/migrate-job.yaml" <<'PY'
import sys, yaml

def container(doc):
    spec = doc["spec"]
    if doc["kind"] == "CronJob":
        spec = spec["jobTemplate"]["spec"]
    return spec["template"]["spec"]["containers"][0]

for path in sys.argv[1:]:
    doc = next(d for d in yaml.safe_load_all(open(path)) if d and d["kind"] in ("Deployment", "CronJob"))
    c = container(doc)
    limits = c["resources"]["limits"]
    memory = limits["memory"].replace("Gi", "g").replace("Mi", "m")
    flags = f"--memory={memory}" + (f" --cpus={int(limits['cpu'].rstrip('m')) / 1000}" if "cpu" in limits else "")
    options = next(e["value"] for e in c["env"] if e["name"] == "JAVA_TOOL_OPTIONS")
    print(f"{flags}|{options}")
PY
)

while IFS='|' read -r flags options; do
  echo "Booting $image from its AOT cache with $flags and $options"
  # shellcheck disable=SC2086 # the flags are one or two, split on purpose
  if ! docker run --rm --network none $flags \
    -e SPRING_PROFILES_ACTIVE=training \
    -e APP_JWT_SECRET="$(key 64)" \
    -e APP_TWOFACTOR_KEY="$(key 32)" \
    -e AUTH_CLIENTS_VAULT_SECRET="$(key 24)" \
    -e JAVA_TOOL_OPTIONS="$options -XX:AOTMode=on -Dspring.context.exit=onRefresh" \
    "$image" >"$log" 2>&1; then
    tail -n 40 "$log"
    echo "::error::$image did not boot from its AOT cache with $flags"
    exit 1
  fi
done <<<"$shapes"
