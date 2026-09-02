#!/usr/bin/env bash
# Generates the Blueshell OpenAPI spec using in-memory H2 (no database
# required) and the frontend TypeScript client from it.
#
# There is no longer an external-spec mode. Brevo and Discord are consumed as
# published client packages from ESA-Blueshell/{brevo,discord}-client, each of
# which re-derives its spec from upstream nightly and releases a version
# describing what actually changed. Nothing external is downloaded here.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/openapi-common.sh
source "$ROOT_DIR/scripts/openapi-common.sh"

if [ "$#" -gt 0 ]; then
  # --blueshell-only used to select the mode that is now the only mode.
  case "$1" in
    --blueshell-only) echo "note: --blueshell-only is the only behaviour now; ignoring." ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
fi

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

normalize_api_spec

# ---- Generate frontend TypeScript clients ----

echo "Installing frontend dependencies..."
yarn --cwd services/frontend install

echo "Generating the frontend TypeScript Blueshell client..."
yarn --cwd services/frontend gen:blueshell
yarn --cwd services/frontend lint:gen || true

echo "OpenAPI spec and frontend clients generated successfully."
