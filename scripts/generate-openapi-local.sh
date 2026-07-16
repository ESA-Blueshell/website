#!/usr/bin/env bash
# Generates the OpenAPI spec using in-memory H2 (no database required),
# downloads external specs, regenerates the Brevo client,
# and generates frontend TypeScript clients.
#
# Pass --blueshell-only (or set BLUESHELL_ONLY=true) to skip everything
# external: no Discord/Brevo download, no Java client regen for those,
# only the Blueshell spec + frontend Blueshell client. CI uses this mode
# because upstream Discord/Brevo specs change too often to gate every PR
# on; a separate scheduled workflow validates the external regen on
# main and surfaces drift for a human-driven update PR.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/openapi-common.sh
source "$ROOT_DIR/scripts/openapi-common.sh"

BLUESHELL_ONLY="${BLUESHELL_ONLY:-false}"
for arg in "$@"; do
  case "$arg" in
    --blueshell-only) BLUESHELL_ONLY=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

# ---- Prerequisites ----

check_common_prerequisites

for cmd in yarn; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Required command not found in PATH: $cmd" >&2
    exit 1
  fi
done

# ---- Generate OpenAPI spec via in-memory H2 ----

echo "Generating OpenAPI spec via in-memory H2..."
./gradlew --no-daemon --build-cache :services:api:dumpOpenApiSpec

# ---- Shared steps ----

if [ "$BLUESHELL_ONLY" = "true" ]; then
  normalize_api_spec
else
  download_external_specs
  regen_brevo_client
  normalize_specs
fi

# ---- Generate frontend TypeScript clients ----

echo "Installing frontend dependencies..."
yarn --cwd services/frontend install

if [ "$BLUESHELL_ONLY" = "true" ]; then
  echo "Generating frontend TypeScript Blueshell client (skipping external specs)..."
  yarn --cwd services/frontend gen:blueshell
  yarn --cwd services/frontend lint:gen || true
else
  echo "Generating frontend TypeScript clients..."
  yarn --cwd services/frontend gen:all
  yarn --cwd services/frontend lint:gen || true
fi

echo "OpenAPI spec and frontend clients generated successfully."
