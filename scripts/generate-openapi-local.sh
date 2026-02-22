#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8080}"
API_SPEC_URL="${API_SPEC_URL:-${API_BASE_URL}/v3/api-docs}"
DISCORD_OPENAPI_URL="${DISCORD_OPENAPI_URL:-https://raw.githubusercontent.com/discord/discord-api-spec/refs/heads/main/specs/openapi.json}"
API_LOG_FILE="${API_LOG_FILE:-openapi-api.log}"
API_STARTUP_RETRIES="${API_STARTUP_RETRIES:-90}"
API_STARTUP_SLEEP_SECONDS="${API_STARTUP_SLEEP_SECONDS:-2}"

for cmd in curl jq yarn; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Required command not found in PATH: $cmd" >&2
    exit 1
  fi
done

if [ ! -d "openapi" ]; then
  echo "openapi directory not found in current directory" >&2
  exit 1
fi

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

echo "Starting API via Gradle (:api:bootRun)..."
./gradlew --no-daemon :api:bootRun > "$API_LOG_FILE" 2>&1 &
API_PID="$!"

echo "Waiting for API OpenAPI endpoint: $API_SPEC_URL"
spec_ready=false
for ((i=1; i<=API_STARTUP_RETRIES; i++)); do
  if curl -fsS "$API_SPEC_URL" -o openapi/blueshell.raw.json; then
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

echo "Downloading Discord OpenAPI spec..."
curl -fsSL "$DISCORD_OPENAPI_URL" -o openapi/discord.raw.json

echo "Normalizing OpenAPI JSON files..."
jq -S -c . openapi/blueshell.raw.json > openapi/blueshell.json
jq -S -c . openapi/discord.raw.json > openapi/discord.json
rm -f openapi/blueshell.raw.json openapi/discord.raw.json

echo "Generating frontend TypeScript clients..."
yarn --cwd frontend gen:all
yarn --cwd frontend lint:gen || true

echo "OpenAPI spec and frontend clients generated successfully."
