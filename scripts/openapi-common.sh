#!/usr/bin/env bash
# openapi-common.sh — shared steps for OpenAPI spec generation scripts.
# Must be sourced after cd'ing into the project root.
#
# Only the Blueshell spec is produced here. The Brevo and Discord specs, their
# filtering and their client generation moved to ESA-Blueshell/brevo-client and
# ESA-Blueshell/discord-client, where a nightly job re-derives them from
# upstream and publishes versioned clients.

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

# The Blueshell spec needs no normalising: the generator test sorts its keys and writes
# block YAML. Kept as a no-op so the call sites read the same for every spec.
normalize_api_spec() {
  if [ ! -f "$API_OPENAPI_SPEC" ]; then
    echo "$API_OPENAPI_SPEC not found" >&2
    exit 1
  fi
}

