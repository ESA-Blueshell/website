#!/usr/bin/env bash
# Generates the OpenAPI spec by starting the API locally via Gradle bootRun,
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

# ---- Configuration ----

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
API_SPEC_URL="${API_SPEC_URL:-${API_BASE_URL}/v3/api-docs}"
API_LOG_FILE="${API_LOG_FILE:-openapi-api.log}"
API_STARTUP_RETRIES="${API_STARTUP_RETRIES:-90}"
API_STARTUP_SLEEP_SECONDS="${API_STARTUP_SLEEP_SECONDS:-2}"

# ---- Prerequisites ----

check_common_prerequisites

for cmd in yarn; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Required command not found in PATH: $cmd" >&2
    exit 1
  fi
done

# ---- Start local API ----

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-test}"
export SPRINGDOC_API_DOCS_ENABLED="${SPRINGDOC_API_DOCS_ENABLED:-true}"
export SPRINGDOC_SWAGGER_UI_ENABLED="${SPRINGDOC_SWAGGER_UI_ENABLED:-true}"
export SECURITY_OPENAPI_PUBLIC_ENABLED="${SECURITY_OPENAPI_PUBLIC_ENABLED:-true}"

API_PID=""
cleanup() {
  if [ -n "$API_PID" ] && kill -0 "$API_PID" >/dev/null 2>&1; then
    kill "$API_PID" >/dev/null 2>&1 || true
    wait "$API_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

echo "Starting API via Gradle (bootRun)..."
./gradlew --no-daemon --build-cache :services:api:bootRun > "$API_LOG_FILE" 2>&1 &
API_PID="$!"

echo "Waiting for API OpenAPI endpoint: $API_SPEC_URL"
spec_ready=false
for ((i = 1; i <= API_STARTUP_RETRIES; i++)); do
  if curl -fsS "$API_SPEC_URL" -o "${API_OPENAPI_SPEC%.json}.raw.json"; then
    spec_ready=true
    break
  fi

  if ! kill -0 "$API_PID" >/dev/null 2>&1; then
    echo "API process exited before OpenAPI endpoint became ready." >&2
    tail -n 120 "$API_LOG_FILE" >&2 || true
    exit 1
  fi

  echo "Waiting for API... ($i/$API_STARTUP_RETRIES)"
  sleep "$API_STARTUP_SLEEP_SECONDS"
done

if [ "$spec_ready" != "true" ]; then
  echo "Timed out waiting for API OpenAPI endpoint: $API_SPEC_URL" >&2
  tail -n 120 "$API_LOG_FILE" >&2 || true
  exit 1
fi

# ---- Shared steps ----

if [ "$BLUESHELL_ONLY" = "true" ]; then
  normalize_api_spec
else
  download_external_specs
  regen_brevo_client
  regen_listmonk_client
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
