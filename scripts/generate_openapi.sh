#!/usr/bin/env bash
# Generates the OpenAPI spec from the running Docker API container,
# downloads external specs, regenerates the Brevo client,
# and generates frontend TypeScript clients.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/openapi-common.sh
source "$ROOT_DIR/scripts/openapi-common.sh"

# ---- Prerequisites ----

check_common_prerequisites

if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose is not available" >&2
  exit 1
fi

if [ ! -f "docker-compose.yml" ]; then
  echo "docker-compose.yml not found in current directory" >&2
  exit 1
fi

# ---- Fetch Blueshell spec from running API container ----

echo "Fetching Blueshell OpenAPI spec from API container..."
docker compose exec -T api sh -c \
  "curl -fsSS http://localhost:8080/v3/api-docs" > "${API_OPENAPI_SPEC%.yaml}.raw.json"

# ---- Shared steps ----

download_external_specs
regen_brevo_client
regen_listmonk_client
normalize_specs

# ---- Generate frontend TypeScript clients ----

echo "Generating frontend TypeScript clients..."
# `up -d frontend` is a no-op when the compose config is unchanged,
# but forces a recreate when mounts or env have drifted (e.g. after
# the openapi.yaml → openapi.json rename). Without this, `exec` runs
# against a stale container that still has the old bind-mounts.
docker compose up -d frontend
docker compose exec frontend sh -c \
  "cd /usr/app && yarn gen:all && (yarn lint:gen || true)"

echo "OpenAPI spec and frontend clients generated successfully."
