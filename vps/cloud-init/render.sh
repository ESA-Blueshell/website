#!/usr/bin/env bash
set -euo pipefail

# render.sh — render the cloud-init config template with real values.
#
# Usage:
#   ./render.sh
#
# Reads all values from vps/.env (or exported env vars).
#
# Generates (or reuses) two ed25519 SSH keypairs in ~/.ssh:
#   ~/.ssh/blueshell-website  — for the website application user
#   ~/.ssh/blueshell-admin    — for the admin user
#
# Auto-generates random secrets for any blank "auto-generated" variable.
#
# Output:
#   cloud-init/cloud-config.yaml
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
Error: missing required values. Set them in vps/.env (or export to the shell):

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

# Hash a password with SHA-512 crypt (for cloud-init chpasswd type: hash).
# Tries openssl passwd -6 (OpenSSL 3.x / Homebrew on macOS, default on Linux),
# then falls back to Python's crypt module (Python < 3.13).
hash_password() {
  local password="$1"
  local hashed
  if hashed="$(openssl passwd -6 "${password}" 2>/dev/null)" && [[ -n "${hashed}" ]]; then
    printf '%s' "${hashed}"
    return
  fi
  if hashed="$(python3 -c "import crypt,sys; print(crypt.crypt(sys.argv[1], crypt.mksalt(crypt.METHOD_SHA512)))" "${password}" 2>/dev/null)" && [[ -n "${hashed}" ]]; then
    printf '%s' "${hashed}"
    return
  fi
  echo "Error: cannot generate SHA-512 password hash." >&2
  echo "Install OpenSSL 3.x (brew install openssl@3) or use Python < 3.13." >&2
  exit 1
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

# ── Domain ───────────────────────────────────────────────────────────────────
# BASE_DOMAIN is the single source of truth. INFRA_DOMAIN is written into
# .infra.env so the infra stack picks it up on first boot without extra config.
export BASE_DOMAIN="${BASE_DOMAIN:-v2.esa-blueshell.nl}"
export INFRA_DOMAIN="${INFRA_DOMAIN:-${BASE_DOMAIN}}"

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
GITHUB_DEPLOY_KEY="${SSH_DIR}/blueshell-website-github-deploy-key"

ensure_key "${WEBSITE_KEY}"       "website@$(hostname -f 2>/dev/null || hostname)"
ensure_key "${ADMIN_KEY}"         "admin@$(hostname -f 2>/dev/null || hostname)"
ensure_key "${GITHUB_DEPLOY_KEY}" "github-deploy@esa-blueshell"

WEBSITE_PUB="$(tr -d '\n' < "${WEBSITE_KEY}.pub")"
ADMIN_PUB="$(tr -d '\n' < "${ADMIN_KEY}.pub")"
GITHUB_DEPLOY_PUB="$(tr -d '\n' < "${GITHUB_DEPLOY_KEY}.pub")"

echo ""
echo "  GitHub deploy key (add as read-only Deploy Key to the repository):"
echo "  Repository: Settings → Deploy keys → Add deploy key → Allow read access"
echo "  Key:"
echo "  ${GITHUB_DEPLOY_PUB}"
echo ""

# ── Hash passwords (SHA-512 crypt) ────────────────────────────────────────────
echo "==> Hashing passwords..."
HASHED_ADMIN="$(hash_password "${ADMIN_PASSWORD}")"
HASHED_ROOT="$(hash_password "${ROOT_PASSWORD}")"
HASHED_WEBSITE="$(hash_password "${WEBSITE_PASSWORD}")"

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
INFRA_DOMAIN=${INFRA_DOMAIN}
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

SAFE_GHCR_TOKEN="$(_escape_sed "${GHCR_TOKEN}")"
SAFE_GHCR_USER="$(_escape_sed "${GHCR_USER}")"
SAFE_WEBSITE_PUB="$(_escape_sed "${WEBSITE_PUB}")"
SAFE_ADMIN_PUB="$(_escape_sed "${ADMIN_PUB}")"
SAFE_INFISICAL_ADMIN_EMAIL="$(_escape_sed "${INFISICAL_ADMIN_EMAIL}")"
SAFE_INFISICAL_ADMIN_PASSWORD="$(_escape_sed "${INFISICAL_ADMIN_PASSWORD}")"
SAFE_GITHUB_DEPLOY_PUB="$(_escape_sed "${GITHUB_DEPLOY_PUB}")"

# Render human-readable placeholders (passwords, SSH keys, GHCR).
# Passwords are substituted via awk because SHA-512 crypt hashes contain '$'
# which bash would expand as shell variables inside double-quoted sed expressions.
_render_common() {
  local src="$1"
  local dst="$2"
  local tmp
  tmp="$(mktemp)"

  # Phase 1: passwords via awk (safe against '$' in SHA-512 crypt hashes;
  # SHA-512 uses alphabet ./0-9A-Za-z so no '&' to worry about in gsub)
  awk \
    -v admin_pw="${HASHED_ADMIN}" \
    -v root_pw="${HASHED_ROOT}" \
    -v website_pw="${HASHED_WEBSITE}" \
    '{
      gsub(/__ADMIN_PASSWORD__/, admin_pw)
      gsub(/__ROOT_PASSWORD__/, root_pw)
      gsub(/__WEBSITE_PASSWORD__/, website_pw)
      print
    }' "${src}" > "${tmp}"

  # Phase 2: remaining values via sed (SSH keys, GHCR, Infisical — no '$')
  sed \
    -e "s|__GHCR_TOKEN__|${SAFE_GHCR_TOKEN}|g" \
    -e "s|__GHCR_USERNAME__|${SAFE_GHCR_USER}|g" \
    -e "s|__WEBSITE_SSH_PUB__|${SAFE_WEBSITE_PUB}|g" \
    -e "s|__ADMIN_SSH_PUB__|${SAFE_ADMIN_PUB}|g" \
    -e "s|__INFISICAL_ADMIN_EMAIL__|${SAFE_INFISICAL_ADMIN_EMAIL}|g" \
    -e "s|__INFISICAL_ADMIN_PASSWORD__|${SAFE_INFISICAL_ADMIN_PASSWORD}|g" \
    -e "s|__GITHUB_DEPLOY_PUB__|${SAFE_GITHUB_DEPLOY_PUB}|g" \
    "${tmp}" > "${dst}"

  rm -f "${tmp}"
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
TMPL="${SCRIPT_DIR}/cloud-config.template.yaml"
OUT="${SCRIPT_DIR}/cloud-config.yaml"

if [[ ! -f "${TMPL}" ]]; then
  echo "Error: template not found: ${TMPL}" >&2
  exit 1
fi

_render_common "${TMPL}" "${OUT}"

# Inject base64-encoded files into the cloud-config
_inject_b64_file "${OUT}" "__PROVISION_SH_B64__"       "${SCRIPT_DIR}/../scripts/provision.sh"
_inject_b64_file "${OUT}" "__DB_BACKUP_SH_B64__"       "${SCRIPT_DIR}/../scripts/db-backup.sh"
_inject_b64_file "${OUT}" "__WEBSITE_CLI_SH_B64__"     "${SCRIPT_DIR}/../scripts/website-cli.sh"
_inject_b64_file "${OUT}" "__DB_ENV_B64__"             "${RENDERED_DIR}/.db.env"
_inject_b64_file "${OUT}" "__API_ENV_B64__"            "${RENDERED_DIR}/.api.env"
_inject_b64_file "${OUT}" "__LISTMONK_ENV_B64__"       "${RENDERED_DIR}/.listmonk.env"
_inject_b64_file "${OUT}" "__INFRA_ENV_B64__"          "${RENDERED_DIR}/.infra.env"
_inject_b64_file "${OUT}" "__GITHUB_DEPLOY_KEY_B64__"  "${GITHUB_DEPLOY_KEY}"

echo ""
echo "==> Wrote ${OUT}"
echo ""
echo "Keys:"
echo "  ${WEBSITE_KEY} (.pub)        -> injected for website user SSH login"
echo "  ${ADMIN_KEY} (.pub)          -> injected for admin user SSH login"
echo "  ${GITHUB_DEPLOY_KEY} (.pub)  -> injected as GitHub deploy key (read-only repo access)"
echo ""
echo "Env files (also embedded in cloud-config):"
echo "  ${RENDERED_DIR}/.db.env"
echo "  ${RENDERED_DIR}/.api.env"
echo "  ${RENDERED_DIR}/.listmonk.env"
echo "  ${RENDERED_DIR}/.infra.env"
