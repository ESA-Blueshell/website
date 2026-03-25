#!/usr/bin/env bash
# openapi-common.sh — shared steps for OpenAPI spec generation scripts.
# Must be sourced after cd'ing into the project root.

DISCORD_OPENAPI_URL="${DISCORD_OPENAPI_URL:-https://raw.githubusercontent.com/discord/discord-api-spec/refs/heads/main/specs/openapi.json}"
BREVO_OPENAPI_URL="${BREVO_OPENAPI_URL:-https://api.brevo.com/v3/swagger_definition_v3.yml}"

SHARED_OPENAPI_DIR="${SHARED_OPENAPI_DIR:-services/shared/openapi}"
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
  services/api/gradlew --no-daemon --build-cache -p services/api :clients:brevo:generate
}

regen_listmonk_client() {
  echo "Regenerating Listmonk Java client..."
  services/api/gradlew --no-daemon --build-cache -p services/api :clients:listmonk:generate
}

# Normalizes the API spec and discord.raw.json in-place.
# Expects the caller to have written the API spec (or raw JSON) and discord.raw.json before calling.
normalize_specs() {
  echo "Normalizing OpenAPI spec files..."
  local tmp
  tmp="$(mktemp)"

  # Use 'cat > file' instead of 'mv' to preserve the file inode.
  # Docker bind mounts for single files track the inode, so 'mv' (which
  # replaces the inode) would silently break any container mount pointing
  # at the target path.

  if [ -f "${API_OPENAPI_SPEC%.yaml}.raw.json" ]; then
    jq -S -c . "${API_OPENAPI_SPEC%.yaml}.raw.json" > "$tmp" && cat "$tmp" > "$API_OPENAPI_SPEC"
    rm -f "${API_OPENAPI_SPEC%.yaml}.raw.json"
  elif [ -f "$API_OPENAPI_SPEC" ]; then
    jq -S -c . "$API_OPENAPI_SPEC" > "$tmp" && cat "$tmp" > "$API_OPENAPI_SPEC"
  else
    echo "$API_OPENAPI_SPEC (or .raw.json) not found" >&2
    rm -f "$tmp"
    exit 1
  fi

  jq -S -c . "$SHARED_OPENAPI_DIR/discord.raw.json" > "$tmp" && cat "$tmp" > "$SHARED_OPENAPI_DIR/discord.json"
  rm -f "$SHARED_OPENAPI_DIR/discord.raw.json" "$tmp"
}