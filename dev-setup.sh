#!/usr/bin/env bash
# =============================================================================
# dev-setup.sh — prepare all secrets and env files for local development.
#
# Safe to run multiple times: existing files are never overwritten.
#
# What this script does:
#   1. Creates services/api/.db.env  (dev MariaDB credentials)
#   2. Creates services/api/.api.env (auto-generated JWT secret + defaults)
#
# After running this script:
#   docker compose up
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RESET='\033[0m'

ok()   { echo -e "  ${GREEN}✓${RESET}  $*"; }
skip() { echo -e "  ${YELLOW}–${RESET}  $* (already exists, skipping)"; }

echo "==> Setting up local development environment..."
echo ""

# ── Helpers ───────────────────────────────────────────────────────────────────

gen_secret() {
  openssl rand -base64 48 | tr -d '\n='
}

# ── services/api/.db.env ──────────────────────────────────────────────────────
DB_ENV="${SCRIPT_DIR}/services/api/.db.env"
if [[ ! -f "${DB_ENV}" ]]; then
  cat > "${DB_ENV}" <<'EOF'
MYSQL_ROOT_PASSWORD=blueshell
MYSQL_DATABASE=blueshell
MYSQL_USER=blueshell
MYSQL_PASSWORD=blueshell
EOF
  ok "Created ${DB_ENV}"
else
  skip "${DB_ENV}"
fi

# ── services/api/.api.env ─────────────────────────────────────────────────────
API_ENV="${SCRIPT_DIR}/services/api/.api.env"
if [[ ! -f "${API_ENV}" ]]; then
  JWT_SECRET="$(gen_secret)"
  cat > "${API_ENV}" <<EOF
JWT_SECRET=${JWT_SECRET}
STORAGE_LOCATION=/home/storage

# SMTP relay (leave blank to disable outbound email)
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_USE_SSL=false
SMTP_USE_TLS=true

# Brevo (optional)
BREVO_API_KEY=

# Google Calendar (optional)
GOOGLE_CALENDAR_ID=
GOOGLE_CALENDAR_SA_JSON=
EOF
  ok "Created ${API_ENV} (JWT_SECRET auto-generated)"
else
  skip "${API_ENV}"
fi

echo ""
echo "==> Done. Start the dev environment with:"
echo ""
echo "    docker compose up"
echo ""
