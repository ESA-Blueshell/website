#!/usr/bin/env bash
# openapi-common.sh — shared steps for OpenAPI spec generation scripts.
# Must be sourced after cd'ing into the project root.

DISCORD_OPENAPI_URL="${DISCORD_OPENAPI_URL:-https://raw.githubusercontent.com/discord/discord-api-spec/refs/heads/main/specs/openapi.json}"
BREVO_OPENAPI_URL="${BREVO_OPENAPI_URL:-https://api.brevo.com/v3/swagger_definition_v3.yml}"

OPENAPI_DIR="${OPENAPI_DIR:-services/api/openapi}"

check_common_prerequisites() {
  for cmd in curl jq; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      echo "Required command not found in PATH: $cmd" >&2
      exit 1
    fi
  done

  if [ ! -d "$OPENAPI_DIR" ]; then
    echo "$OPENAPI_DIR directory not found in current directory" >&2
    exit 1
  fi
}

download_external_specs() {
  echo "Downloading Discord OpenAPI spec..."
  curl -fsSL "$DISCORD_OPENAPI_URL" -o "$OPENAPI_DIR/discord.raw.json"

  echo "Downloading Brevo OpenAPI spec..."
  curl -fsSL "$BREVO_OPENAPI_URL" -o "$OPENAPI_DIR/brevo.yml"
}

regen_brevo_client() {
  echo "Regenerating Brevo Java client..."
  services/api/gradlew :brevo-client:generate
}

regen_listmonk_client() {
  echo "Regenerating Listmonk Java client..."
  services/api/gradlew :listmonk-client:generate
}

# Normalizes services/api/openapi/blueshell.{raw.}json and discord.raw.json in-place.
# Expects the caller to have written blueshell.raw.json (or blueshell.json) before calling.
normalize_json_specs() {
  echo "Normalizing OpenAPI JSON files..."
  local tmp
  tmp="$(mktemp)"

  if [ -f "$OPENAPI_DIR/blueshell.raw.json" ]; then
    jq -S -c . "$OPENAPI_DIR/blueshell.raw.json" > "$tmp" && mv "$tmp" "$OPENAPI_DIR/blueshell.json"
    rm -f "$OPENAPI_DIR/blueshell.raw.json"
  elif [ -f "$OPENAPI_DIR/blueshell.json" ]; then
    jq -S -c . "$OPENAPI_DIR/blueshell.json" > "$tmp" && mv "$tmp" "$OPENAPI_DIR/blueshell.json"
  else
    echo "$OPENAPI_DIR/blueshell.json (or .raw.json) not found" >&2
    rm -f "$tmp"
    exit 1
  fi

  jq -S -c . "$OPENAPI_DIR/discord.raw.json" > "$tmp" && mv "$tmp" "$OPENAPI_DIR/discord.json"
  rm -f "$OPENAPI_DIR/discord.raw.json"
}
