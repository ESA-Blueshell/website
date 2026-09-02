#!/usr/bin/env bash
# openapi-common.sh — shared steps for OpenAPI spec generation scripts.
# Must be sourced after cd'ing into the project root.

DISCORD_OPENAPI_URL="${DISCORD_OPENAPI_URL:-https://raw.githubusercontent.com/discord/discord-api-spec/refs/heads/main/specs/openapi.json}"
BREVO_OPENAPI_URL="${BREVO_OPENAPI_URL:-https://api.brevo.com/v3/swagger_definition_v3.yml}"

SHARED_OPENAPI_DIR="${SHARED_OPENAPI_DIR:-libs/openapi-specs}"
# Block-style YAML with its keys sorted, written by the generator test. One line per
# value, so two branches that each add an endpoint conflict only where they disagree
# rather than on the single line a minified document is.
API_OPENAPI_SPEC="${API_OPENAPI_SPEC:-services/api/openapi.yaml}"

check_common_prerequisites() {
  for cmd in curl jq; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      echo "Required command not found in PATH: $cmd" >&2
      exit 1
    fi
  done

  if [ ! -d "$SHARED_OPENAPI_DIR" ]; then
    echo "$SHARED_OPENAPI_DIR directory not found in current directory" >&2
    exit 1
  fi
}

download_external_specs() {
  echo "Downloading Discord OpenAPI spec..."
  curl -fsSL "$DISCORD_OPENAPI_URL" -o "$SHARED_OPENAPI_DIR/discord.raw.json"

  echo "Downloading Brevo OpenAPI spec..."
  curl -fsSL "$BREVO_OPENAPI_URL" -o "$SHARED_OPENAPI_DIR/brevo.yml"
}

regen_brevo_client() {
  echo "Regenerating Brevo Java client..."
  ./gradlew --no-daemon --build-cache :libs:clients:brevo:generate
}

# The Blueshell spec needs no normalising: the generator test sorts its keys and writes
# block YAML. Kept as a no-op so the call sites read the same for every spec.
normalize_api_spec() {
  if [ ! -f "$API_OPENAPI_SPEC" ]; then
    echo "$API_OPENAPI_SPEC not found" >&2
    exit 1
  fi
}

# Normalizes the Discord spec in-place. Caller must have downloaded
# discord.raw.json upstream (download_external_specs).
normalize_discord_spec() {
  echo "Normalizing Discord OpenAPI spec..."
  local tmp
  tmp="$(mktemp)"

  jq -S -c . "$SHARED_OPENAPI_DIR/discord.raw.json" > "$tmp" && mv "$tmp" "$SHARED_OPENAPI_DIR/discord.json"
  rm -f "$SHARED_OPENAPI_DIR/discord.raw.json"
}

# Convenience: normalize both Blueshell + Discord specs.
normalize_specs() {
  normalize_api_spec
  normalize_discord_spec
}