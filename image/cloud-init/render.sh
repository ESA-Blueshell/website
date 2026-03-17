#!/usr/bin/env bash
set -euo pipefail

# render.sh
# Usage:
#   ./render.sh '<BLUESHELL_PASSWORD>' '<ROOT_PASSWORD>' '<WEBSITE_PASSWORD>' '<GHCR_USERNAME>' '<GHCR_TOKEN>'
#
# - Generates (or reuses) two SSH keypairs in ~/.ssh:
#     ~/.ssh/blueshell-website   (for the website user)
#     ~/.ssh/blueshell-admin     (for the blueshell admin user)
# - Replaces placeholders for passwords, SSH authorized keys, and GHCR auth.
# - Outputs cloud-config.yaml in this directory.
#
# Notes:
# - The GHCR_TOKEN should be a GitHub PAT (classic) with only read:packages.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_FILE="${SCRIPT_DIR}/cloud-config.template.yaml"
OUTPUT_FILE="${SCRIPT_DIR}/cloud-config.yaml"

if [[ $# -lt 5 ]]; then
  cat >&2 <<'EOF'
Usage:
  render.sh '<BLUESHELL_PASSWORD>' '<ROOT_PASSWORD>' '<WEBSITE_PASSWORD>' '<GHCR_USERNAME>' '<GHCR_TOKEN>'

Notes:
  - All three passwords are required (for console/portal login only; SSH remains keys-only).
  - GHCR_USERNAME and GHCR_TOKEN are required; only the website user will be logged in during runcmd.
EOF
  exit 1
fi

BLUESHELL_PASSWORD="$1"
ROOT_PASSWORD="$2"
WEBSITE_PASSWORD="$3"
GHCR_USER="$4"
GHCR_TOKEN="$5"
shift 5 || true

if [[ ! -f "$TEMPLATE_FILE" ]]; then
  echo "Template not found: $TEMPLATE_FILE" >&2
  exit 1
fi

# Ensure ~/.ssh exists
SSH_DIR="${HOME}/.ssh"
mkdir -p "${SSH_DIR}"
chmod 700 "${SSH_DIR}"

# Generate or reuse a named key (ed25519, empty passphrase)
ensure_key() {
  local key_path="$1"
  local comment="$2"
  if [[ -f "${key_path}" && -f "${key_path}.pub" ]]; then
    echo "Reusing existing key: ${key_path}"
  else
    echo "Generating key: ${key_path}"
    ssh-keygen -q -t ed25519 -N "" -C "${comment}" -f "${key_path}"
  fi
  chmod 600 "${key_path}"
  chmod 644 "${key_path}.pub"
}

WEBSITE_KEY="${SSH_DIR}/blueshell-website"
ADMIN_KEY="${SSH_DIR}/blueshell-admin"

ensure_key "${WEBSITE_KEY}"  "website@$(hostname -f 2>/dev/null || hostname)"
ensure_key "${ADMIN_KEY}"    "admin@$(hostname -f 2>/dev/null || hostname)"

WEBSITE_PUB="$(tr -d '\n' < "${WEBSITE_KEY}.pub")"
ADMIN_PUB="$(tr -d '\n' < "${ADMIN_KEY}.pub")"

# Escape values for sed (/, &, and \)
_escape_sed() {
  printf '%s' "$1" | sed -e 's/[\/&\\]/\\&/g'
}

# Placeholders in the template
PH_BLUESHELL_PW="__BLUESHELL_PASSWORD__"
PH_ROOT_PW="__ROOT_PASSWORD__"
PH_WEBSITE_PW="__WEBSITE_PASSWORD__"
PH_GHCR_TOKEN="__GHCR_TOKEN__"
PH_GHCR_USER="__GHCR_USERNAME__"
PH_WEBSITE_PUB="__WEBSITE_SSH_PUB__"
PH_ADMIN_PUB="__ADMIN_SSH_PUB__"

SAFE_BLUESHELL="$(_escape_sed "${BLUESHELL_PASSWORD}")"
SAFE_ROOT="$(_escape_sed "${ROOT_PASSWORD}")"
SAFE_WEBSITE="$(_escape_sed "${WEBSITE_PASSWORD}")"
SAFE_GHCR_TOKEN="$(_escape_sed "${GHCR_TOKEN}")"
SAFE_GHCR_USER="$(_escape_sed "${GHCR_USER}")"
SAFE_WEBSITE_PUB="$(_escape_sed "${WEBSITE_PUB}")"
SAFE_ADMIN_PUB="$(_escape_sed "${ADMIN_PUB}")"

# Render the template -> OUTPUT_FILE
sed \
  -e "s|${PH_BLUESHELL_PW}|${SAFE_BLUESHELL}|g" \
  -e "s|${PH_ROOT_PW}|${SAFE_ROOT}|g" \
  -e "s|${PH_WEBSITE_PW}|${SAFE_WEBSITE}|g" \
  -e "s|${PH_GHCR_TOKEN}|${SAFE_GHCR_TOKEN}|g" \
  -e "s|${PH_GHCR_USER}|${SAFE_GHCR_USER}|g" \
  -e "s|${PH_WEBSITE_PUB}|${SAFE_WEBSITE_PUB}|g" \
  -e "s|${PH_ADMIN_PUB}|${SAFE_ADMIN_PUB}|g" \
  "${TEMPLATE_FILE}" > "${OUTPUT_FILE}"

echo "Wrote ${OUTPUT_FILE}"
echo "Keys:"
echo "  ${WEBSITE_KEY} (.pub)  -> injected for website"
echo "  ${ADMIN_KEY} (.pub)    -> injected for blueshell (admin)"
