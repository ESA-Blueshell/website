#!/usr/bin/env bash
set -euo pipefail

# render.sh — render cloud-init config template(s) with real values.
#
# Usage:
#   ./render.sh [--standalone|--all] ['<BLU_PW>' '<ROOT_PW>' '<WEB_PW>' '<GHCR_USER>' '<GHCR_TOKEN>']
#
# Flags (first argument, optional):
#   (none)       Render cloud-config.yaml (Packer flow)
#   --standalone Render cloud-config-standalone.yaml (Contabo cloud-init-only flow)
#   --all        Render both
#
# All value arguments are optional if the corresponding env vars are set
# (sourced from image/.env or exported in the shell):
#   BLUESHELL_PASSWORD, ROOT_PASSWORD, WEBSITE_PASSWORD, GHCR_USERNAME, GHCR_TOKEN
#
# Generates (or reuses) two ed25519 SSH keypairs in ~/.ssh:
#   ~/.ssh/blueshell-website  — for the website application user
#   ~/.ssh/blueshell-admin    — for the blueshell admin user
#
# The GHCR_TOKEN should be a GitHub PAT (classic) with only read:packages scope.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Mode selection ────────────────────────────────────────────────────────────
MODE="packer"
if [[ "${1:-}" == "--standalone" ]]; then MODE="standalone"; shift; fi
if [[ "${1:-}" == "--all" ]];        then MODE="all";        shift; fi

# ── Source .env from image root if present ───────────────────────────────────
ENV_FILE="${SCRIPT_DIR}/../.env"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  set -a; source "${ENV_FILE}"; set +a
fi

# ── Values: positional args override env vars ────────────────────────────────
BLUESHELL_PASSWORD="${1:-${BLUESHELL_PASSWORD:-}}"
ROOT_PASSWORD="${2:-${ROOT_PASSWORD:-}}"
WEBSITE_PASSWORD="${3:-${WEBSITE_PASSWORD:-}}"
GHCR_USER="${4:-${GHCR_USERNAME:-}}"
GHCR_TOKEN="${5:-${GHCR_TOKEN:-}}"
shift 5 2>/dev/null || true

if [[ -z "${BLUESHELL_PASSWORD}" || -z "${ROOT_PASSWORD}" || -z "${WEBSITE_PASSWORD}" \
   || -z "${GHCR_USER}" || -z "${GHCR_TOKEN}" ]]; then
  cat >&2 <<'EOF'
Error: missing required values. Provide them as positional arguments or via env vars
(set in image/.env or exported in the shell):

  render.sh [--standalone|--all] '<BLUESHELL_PW>' '<ROOT_PW>' '<WEBSITE_PW>' '<GHCR_USER>' '<GHCR_TOKEN>'

  or set: BLUESHELL_PASSWORD, ROOT_PASSWORD, WEBSITE_PASSWORD, GHCR_USERNAME, GHCR_TOKEN

Notes:
  - All three passwords are for console/portal login only; SSH remains keys-only.
  - GHCR_TOKEN must be a GitHub PAT (classic) with read:packages scope.
EOF
  exit 1
fi

# ── SSH keypairs ──────────────────────────────────────────────────────────────
SSH_DIR="${HOME}/.ssh"
mkdir -p "${SSH_DIR}"
chmod 700 "${SSH_DIR}"

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

ensure_key "${WEBSITE_KEY}" "website@$(hostname -f 2>/dev/null || hostname)"
ensure_key "${ADMIN_KEY}"   "admin@$(hostname -f 2>/dev/null || hostname)"

WEBSITE_PUB="$(tr -d '\n' < "${WEBSITE_KEY}.pub")"
ADMIN_PUB="$(tr -d '\n' < "${ADMIN_KEY}.pub")"

# ── Helpers ───────────────────────────────────────────────────────────────────

# Escape values for use in sed s|...|...|g expressions (/, &, \)
_escape_sed() {
  printf '%s' "$1" | sed -e 's/[\/&\\]/\\&/g'
}

SAFE_BLUESHELL="$(_escape_sed "${BLUESHELL_PASSWORD}")"
SAFE_ROOT="$(_escape_sed "${ROOT_PASSWORD}")"
SAFE_WEBSITE="$(_escape_sed "${WEBSITE_PASSWORD}")"
SAFE_GHCR_TOKEN="$(_escape_sed "${GHCR_TOKEN}")"
SAFE_GHCR_USER="$(_escape_sed "${GHCR_USER}")"
SAFE_WEBSITE_PUB="$(_escape_sed "${WEBSITE_PUB}")"
SAFE_ADMIN_PUB="$(_escape_sed "${ADMIN_PUB}")"

# Render human-readable placeholders (passwords, SSH keys, GHCR) via sed
_render_common() {
  local src="$1"
  local dst="$2"
  sed \
    -e "s|__BLUESHELL_PASSWORD__|${SAFE_BLUESHELL}|g" \
    -e "s|__ROOT_PASSWORD__|${SAFE_ROOT}|g" \
    -e "s|__WEBSITE_PASSWORD__|${SAFE_WEBSITE}|g" \
    -e "s|__GHCR_TOKEN__|${SAFE_GHCR_TOKEN}|g" \
    -e "s|__GHCR_USERNAME__|${SAFE_GHCR_USER}|g" \
    -e "s|__WEBSITE_SSH_PUB__|${SAFE_WEBSITE_PUB}|g" \
    -e "s|__ADMIN_SSH_PUB__|${SAFE_ADMIN_PUB}|g" \
    "${src}" > "${dst}"
}

# Substitute __PROVISION_SH_B64__ using awk (base64 contains / and = which
# confuse sed even with escaping; awk gsub handles them cleanly)
_inject_provision_sh() {
  local file="$1"
  local provision_src="${SCRIPT_DIR}/../scripts/provision.sh"
  if [[ ! -f "${provision_src}" ]]; then
    echo "Error: provision.sh not found at ${provision_src}" >&2
    exit 1
  fi
  local b64_tmp
  b64_tmp="$(mktemp)"
  trap 'rm -f "${b64_tmp}"' RETURN
  base64 < "${provision_src}" | tr -d '\n' > "${b64_tmp}"

  local stage_tmp
  stage_tmp="$(mktemp)"
  trap 'rm -f "${stage_tmp}"' RETURN
  cp "${file}" "${stage_tmp}"

  awk 'NR==FNR{b64=$0; next} {gsub(/__PROVISION_SH_B64__/, b64); print}' \
    "${b64_tmp}" "${stage_tmp}" > "${file}"
}

# ── Render Packer template ────────────────────────────────────────────────────
render_packer() {
  local tmpl="${SCRIPT_DIR}/cloud-config.template.yaml"
  local out="${SCRIPT_DIR}/cloud-config.yaml"
  if [[ ! -f "${tmpl}" ]]; then
    echo "Template not found: ${tmpl}" >&2; exit 1
  fi
  _render_common "${tmpl}" "${out}"
  echo "Wrote ${out}"
}

# ── Render standalone template (Contabo cloud-init-only) ─────────────────────
render_standalone() {
  local tmpl="${SCRIPT_DIR}/cloud-config-standalone.template.yaml"
  local out="${SCRIPT_DIR}/cloud-config-standalone.yaml"
  if [[ ! -f "${tmpl}" ]]; then
    echo "Template not found: ${tmpl}" >&2; exit 1
  fi
  _render_common "${tmpl}" "${out}"
  _inject_provision_sh "${out}"
  echo "Wrote ${out}"
}

# ── Execute ───────────────────────────────────────────────────────────────────
case "${MODE}" in
  packer)     render_packer ;;
  standalone) render_standalone ;;
  all)        render_packer; render_standalone ;;
esac

echo "Keys:"
echo "  ${WEBSITE_KEY} (.pub)  -> injected for website"
echo "  ${ADMIN_KEY} (.pub)    -> injected for blueshell (admin)"
