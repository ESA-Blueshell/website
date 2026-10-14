#!/usr/bin/env bash
# Boots an api image as far as a refreshed context under -XX:AOTMode=on, in the memory and CPU each
# pod gets, so a cache a pod's JVM would ignore fails the build rather than a release (architecture
# ADR-013). The keys are drawn here for the same reason the training run draws its own.
set -euo pipefail

image=${1:?usage: check-aot-cache.sh IMAGE}
log=$(mktemp)
trap 'rm -f "$log"' EXIT

key() { head -c "$1" /dev/urandom | base64 | tr -d '\n'; }

# The api's Deployment, then the migrate Job.
for shape in "--cpus=1.5 --memory=2g" "--memory=1536m"; do
  echo "Booting $image from its AOT cache with $shape"
  # shellcheck disable=SC2086 # the shape is two flags or one, split on purpose
  if ! docker run --rm --network none $shape \
    -e SPRING_PROFILES_ACTIVE=training \
    -e APP_JWT_SECRET="$(key 64)" \
    -e APP_TWOFACTOR_KEY="$(key 32)" \
    -e AUTH_CLIENTS_VAULT_SECRET="$(key 24)" \
    -e JAVA_TOOL_OPTIONS="-XX:AOTMode=on -Dspring.context.exit=onRefresh" \
    "$image" >"$log" 2>&1; then
    tail -n 40 "$log"
    echo "::error::$image did not boot from its AOT cache with $shape"
    exit 1
  fi
done
