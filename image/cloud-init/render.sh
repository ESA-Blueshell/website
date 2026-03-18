#!/usr/bin/env bash
set -euo pipefail

# render.sh — render the cloud-init config template with real values.
#
# Usage:
#   ./render.sh
#
# Reads all values from image/.env (or exported env vars).
#
# Generates (or reuses) two ed25519 SSH keypairs in ~/.ssh:
#   ~/.ssh/blueshell-website  — for the website application user
#   ~/.ssh/blueshell-admin    — for the admin user
#
# Auto-generates random secrets for any blank "auto-generated" variable.
#
# Output:
#   cloud-init/cloud-config-standalone.yaml
#   cloud-init/rendered/.db.env
#   cloud-init/rendered/.api.env
#   cloud-init/rendered/.listmonk.env
#   cloud-init/rendered/.infra.env

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Source .env from image root if present ───────────────────────────────────
ENV_FILE="${SCRIPT_DIR}/../.env"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  set -a; source "${ENV_FILE}"; set +a
fi

# ── Validate required values ─────────────────────────────────────────────────
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
ROOT_PASSWORD="${ROOT_PASSWORD:-}"
WEBSITE_PASSWORD="${WEBSITE_PASSWORD:-}"
GHCR_USER="${GHCR_USERNAME:-}"
GHCR_TOKEN="${GHCR_TOKEN:-}"

if [[ -z "${ADMIN_PASSWORD}" || -z "${ROOT_PASSWORD}" || -z "${WEBSITE_PASSWORD}" \
   || -z "${GHCR_USER}" || -z "${GHCR_TOKEN}" ]]; then
  cat >&2 <<'EOF'
Error: missing required values. Set them in image/.env (or export to the shell):

  ADMIN_PASSWORD       sudo admin user password (console/portal login only)
  ROOT_PASSWORD        root account password (emergency portal access)
  WEBSITE_PASSWORD     application service account password
  GHCR_USERNAME        GitHub username
  GHCR_TOKEN           GitHub PAT (classic) with read:packages scope
EOF
  exit 1
fi

# ── Auto-generate secrets if blank ───────────────────────────────────────────
auto_secret() {
  local var="$1" length="${2:-32}"
  if [[ -z "${!var:-}" ]]; then
    eval "export ${var}=$(openssl rand -base64 "${length}" | tr -d '\n')"
    echo "  auto-generated: ${var}"
  fi
}

auto_hex() {
  local var="$1" bytes="${2:-16}"
  if [[ -z "${!var:-}" ]]; then
    eval "export ${var}=$(openssl rand -hex "${bytes}")"
    echo "  auto-generated: ${var}"
  fi
}

echo "==> Generating secrets..."
auto_secret MYSQL_ROOT_PASSWORD
auto_secret MYSQL_PASSWORD
auto_secret JWT_SECRET 48
auto_secret LISTMONK_DB_PASSWORD
auto_secret LISTMONK_ADMIN_PASSWORD
auto_secret GRAFANA_ADMIN_PASSWORD
auto_secret INFISICAL_DB_PASSWORD
auto_secret INFISICAL_REDIS_PASSWORD
auto_secret INFISICAL_AUTH_SECRET 48
auto_secret INFISICAL_ADMIN_PASSWORD
auto_hex    INFISICAL_ENCRYPTION_KEY 16  # 16 bytes = 32 hex chars

# ── Apply defaults for optional vars ─────────────────────────────────────────
export MYSQL_DATABASE="${MYSQL_DATABASE:-blueshell}"
export MYSQL_USER="${MYSQL_USER:-blueshell}"
export SMTP_HOST="${SMTP_HOST:-}"
export SMTP_PORT="${SMTP_PORT:-587}"
export SMTP_USERNAME="${SMTP_USERNAME:-}"
export SMTP_PASSWORD="${SMTP_PASSWORD:-}"
export SMTP_USE_SSL="${SMTP_USE_SSL:-false}"
export SMTP_USE_TLS="${SMTP_USE_TLS:-true}"
export BREVO_API_KEY="${BREVO_API_KEY:-}"
export GOOGLE_CALENDAR_ID="${GOOGLE_CALENDAR_ID:-}"
export GOOGLE_CALENDAR_SA_JSON="${GOOGLE_CALENDAR_SA_JSON:-}"
export LISTMONK_ADMIN_USERNAME="${LISTMONK_ADMIN_USERNAME:-admin}"
export LISTMONK_ADMIN_EMAIL="${LISTMONK_ADMIN_EMAIL:-}"
export LISTMONK_SMTP_HOST="${LISTMONK_SMTP_HOST:-}"
export LISTMONK_SMTP_PORT="${LISTMONK_SMTP_PORT:-587}"
export LISTMONK_SMTP_USERNAME="${LISTMONK_SMTP_USERNAME:-}"
export LISTMONK_SMTP_PASSWORD="${LISTMONK_SMTP_PASSWORD:-}"
export GRAFANA_DISCORD_WEBHOOK_URL="${GRAFANA_DISCORD_WEBHOOK_URL:-}"
export INFISICAL_ADMIN_EMAIL="${INFISICAL_ADMIN_EMAIL:-}"

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

# ── Render env files ─────────────────────────────────────────────────────────
RENDERED_DIR="${SCRIPT_DIR}/rendered"
mkdir -p "${RENDERED_DIR}"

cat > "${RENDERED_DIR}/.db.env" <<DBEOF
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
MYSQL_DATABASE=${MYSQL_DATABASE}
MYSQL_USER=${MYSQL_USER}
MYSQL_PASSWORD=${MYSQL_PASSWORD}
DBEOF

cat > "${RENDERED_DIR}/.api.env" <<APIEOF
JWT_SECRET=${JWT_SECRET}
SMTP_HOST=${SMTP_HOST}
SMTP_PORT=${SMTP_PORT}
SMTP_USERNAME=${SMTP_USERNAME}
SMTP_PASSWORD=${SMTP_PASSWORD}
SMTP_USE_SSL=${SMTP_USE_SSL}
SMTP_USE_TLS=${SMTP_USE_TLS}
BREVO_API_KEY=${BREVO_API_KEY}
GOOGLE_CALENDAR_ID=${GOOGLE_CALENDAR_ID}
GOOGLE_CALENDAR_SA_JSON=${GOOGLE_CALENDAR_SA_JSON}
APIEOF

cat > "${RENDERED_DIR}/.listmonk.env" <<LMEOF
LISTMONK_DB_PASSWORD=${LISTMONK_DB_PASSWORD}
LISTMONK_ADMIN_USERNAME=${LISTMONK_ADMIN_USERNAME}
LISTMONK_ADMIN_PASSWORD=${LISTMONK_ADMIN_PASSWORD}
LISTMONK_ADMIN_EMAIL=${LISTMONK_ADMIN_EMAIL}
LISTMONK_SMTP_HOST=${LISTMONK_SMTP_HOST}
LISTMONK_SMTP_PORT=${LISTMONK_SMTP_PORT}
LISTMONK_SMTP_USERNAME=${LISTMONK_SMTP_USERNAME}
LISTMONK_SMTP_PASSWORD=${LISTMONK_SMTP_PASSWORD}
LMEOF

cat > "${RENDERED_DIR}/.infra.env" <<INFRAEOF
GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}
GRAFANA_DISCORD_WEBHOOK_URL=${GRAFANA_DISCORD_WEBHOOK_URL}
INFISICAL_DB_PASSWORD=${INFISICAL_DB_PASSWORD}
INFISICAL_REDIS_PASSWORD=${INFISICAL_REDIS_PASSWORD}
INFISICAL_ENCRYPTION_KEY=${INFISICAL_ENCRYPTION_KEY}
INFISICAL_AUTH_SECRET=${INFISICAL_AUTH_SECRET}
INFRAEOF

echo "Rendered env files in ${RENDERED_DIR}/"

# ── Helpers ───────────────────────────────────────────────────────────────────

# Escape values for use in sed s|...|...|g expressions (/, &, \)
_escape_sed() {
  printf '%s' "$1" | sed -e 's/[\/&\\]/\\&/g'
}

SAFE_ADMIN="$(_escape_sed "${ADMIN_PASSWORD}")"
SAFE_ROOT="$(_escape_sed "${ROOT_PASSWORD}")"
SAFE_WEBSITE="$(_escape_sed "${WEBSITE_PASSWORD}")"
SAFE_GHCR_TOKEN="$(_escape_sed "${GHCR_TOKEN}")"
SAFE_GHCR_USER="$(_escape_sed "${GHCR_USER}")"
SAFE_WEBSITE_PUB="$(_escape_sed "${WEBSITE_PUB}")"
SAFE_ADMIN_PUB="$(_escape_sed "${ADMIN_PUB}")"
SAFE_INFISICAL_ADMIN_EMAIL="$(_escape_sed "${INFISICAL_ADMIN_EMAIL}")"
SAFE_INFISICAL_ADMIN_PASSWORD="$(_escape_sed "${INFISICAL_ADMIN_PASSWORD}")"

# Render human-readable placeholders (passwords, SSH keys, GHCR) via sed
_render_common() {
  local src="$1"
  local dst="$2"
  sed \
    -e "s|__ADMIN_PASSWORD__|${SAFE_ADMIN}|g" \
    -e "s|__ROOT_PASSWORD__|${SAFE_ROOT}|g" \
    -e "s|__WEBSITE_PASSWORD__|${SAFE_WEBSITE}|g" \
    -e "s|__GHCR_TOKEN__|${SAFE_GHCR_TOKEN}|g" \
    -e "s|__GHCR_USERNAME__|${SAFE_GHCR_USER}|g" \
    -e "s|__WEBSITE_SSH_PUB__|${SAFE_WEBSITE_PUB}|g" \
    -e "s|__ADMIN_SSH_PUB__|${SAFE_ADMIN_PUB}|g" \
    -e "s|__INFISICAL_ADMIN_EMAIL__|${SAFE_INFISICAL_ADMIN_EMAIL}|g" \
    -e "s|__INFISICAL_ADMIN_PASSWORD__|${SAFE_INFISICAL_ADMIN_PASSWORD}|g" \
    "${src}" > "${dst}"
}

# Substitute a __PLACEHOLDER__ using awk (base64 contains / and = which
# confuse sed even with escaping; awk gsub handles them cleanly)
_inject_b64_file() {
  local file="$1"
  local placeholder="$2"
  local source_file="$3"

  if [[ ! -f "${source_file}" ]]; then
    echo "Error: ${source_file} not found" >&2
    exit 1
  fi
  local b64_tmp
  b64_tmp="$(mktemp)"
  base64 < "${source_file}" | tr -d '\n' > "${b64_tmp}"

  local stage_tmp
  stage_tmp="$(mktemp)"
  cp "${file}" "${stage_tmp}"

  awk -v placeholder="${placeholder}" 'NR==FNR{b64=$0; next} {gsub(placeholder, b64); print}' \
    "${b64_tmp}" "${stage_tmp}" > "${file}"

  rm -f "${b64_tmp}" "${stage_tmp}"
}

# ── Render ────────────────────────────────────────────────────────────────────
TMPL="${SCRIPT_DIR}/cloud-config-standalone.template.yaml"
OUT="${SCRIPT_DIR}/cloud-config-standalone.yaml"

if [[ ! -f "${TMPL}" ]]; then
  echo "Error: template not found: ${TMPL}" >&2
  exit 1
fi

_render_common "${TMPL}" "${OUT}"

# Inject base64-encoded files into the cloud-config
_inject_b64_file "${OUT}" "__PROVISION_SH_B64__" "${SCRIPT_DIR}/../scripts/provision.sh"
_inject_b64_file "${OUT}" "__DB_ENV_B64__"       "${RENDERED_DIR}/.db.env"
_inject_b64_file "${OUT}" "__API_ENV_B64__"      "${RENDERED_DIR}/.api.env"
_inject_b64_file "${OUT}" "__LISTMONK_ENV_B64__" "${RENDERED_DIR}/.listmonk.env"
_inject_b64_file "${OUT}" "__INFRA_ENV_B64__"    "${RENDERED_DIR}/.infra.env"

echo ""
echo "Wrote ${OUT}"
echo ""
echo "Keys:"
echo "  ${WEBSITE_KEY} (.pub)  -> injected for website"
echo "  ${ADMIN_KEY} (.pub)    -> injected for admin"
echo ""
echo "Env files (also embedded in cloud-config):"
echo "  ${RENDERED_DIR}/.db.env"
echo "  ${RENDERED_DIR}/.api.env"
echo "  ${RENDERED_DIR}/.listmonk.env"
echo "  ${RENDERED_DIR}/.infra.env"
