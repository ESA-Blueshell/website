#!/usr/bin/env bash
# =============================================================================
# dev-setup.sh — prepare all secrets and env files for local development.
#
# Safe to run multiple times — existing files and certs are never overwritten.
#
# What this script does:
#   1. Creates services/api/.db.env          (dev MariaDB credentials)
#   2. Creates services/api/.api.env         (auto-generated JWT secret + defaults)
#   3. Creates services/listmonk/.listmonk.env (dev Listmonk credentials)
#   4. Generates a self-signed CA + TLS cert for the dev mailserver
#      (required by docker-mailserver with SSL_TYPE=self-signed)
#
# After running this script:
#   docker compose -f docker-compose.dev.yml up
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

# ── services/listmonk/.listmonk.env ───────────────────────────────────────────
LISTMONK_ENV="${SCRIPT_DIR}/services/listmonk/.listmonk.env"
if [[ ! -f "${LISTMONK_ENV}" ]]; then
  # Credentials match the hardcoded values in services/listmonk/docker-compose.dev.yml
  # so Listmonk and the API agree without extra config.
  cat > "${LISTMONK_ENV}" <<'EOF'
LISTMONK_DB_PASSWORD=listmonk
LISTMONK_ADMIN_USERNAME=listmonk
LISTMONK_ADMIN_PASSWORD=listmonk
LISTMONK_ADMIN_EMAIL=admin@listmonk.local
LISTMONK_ADMIN_API_USER=api

# SMTP (leave blank — the dev mailserver is configured directly in docker-compose)
LISTMONK_SMTP_HOST=
LISTMONK_SMTP_PORT=587
LISTMONK_SMTP_AUTH_PROTOCOL=plain
LISTMONK_SMTP_USERNAME=
LISTMONK_SMTP_PASSWORD=
LISTMONK_SMTP_HELLO_HOSTNAME=
LISTMONK_SMTP_TLS_TYPE=starttls
LISTMONK_SMTP_TLS_SKIP_VERIFY=false

# Bounce mailbox (disabled for dev — dev mailserver handles this directly)
LISTMONK_BOUNCE_MAILBOX_ENABLED=false
LISTMONK_BOUNCE_MAILBOX_HOST=
LISTMONK_BOUNCE_MAILBOX_PORT=993
LISTMONK_BOUNCE_MAILBOX_USERNAME=
LISTMONK_BOUNCE_MAILBOX_PASSWORD=
LISTMONK_BOUNCE_MAILBOX_TLS_ENABLED=true
LISTMONK_BOUNCE_MAILBOX_TLS_SKIP_VERIFY=false
LISTMONK_BOUNCE_MAILBOX_FOLDER=INBOX
LISTMONK_BOUNCE_MAILBOX_RETURN_PATH=
LISTMONK_BOUNCE_MAILBOX_SCAN_INTERVAL=10m
EOF
  ok "Created ${LISTMONK_ENV}"
else
  skip "${LISTMONK_ENV}"
fi

# ── Mailserver self-signed TLS certificates ───────────────────────────────────
# docker-mailserver with SSL_TYPE=self-signed requires these three files:
#   config.dev/ssl/<hostname>-key.pem   — server private key
#   config.dev/ssl/<hostname>-cert.pem  — server certificate (signed by CA)
#   config.dev/ssl/demoCA/cacert.pem    — CA certificate
# config.dev/ is mounted as /tmp/docker-mailserver/ inside the container.
# The config.dev/.gitignore ignores everything in this directory except
# the explicitly tracked config files, so generated certs are never committed.

MAIL_HOSTNAME="mail.dev.local"
SSL_DIR="${SCRIPT_DIR}/services/mailserver/config.dev/ssl"
CA_DIR="${SSL_DIR}/demoCA"
CA_KEY="${CA_DIR}/cakey.pem"
CA_CERT="${CA_DIR}/cacert.pem"
SERVER_KEY="${SSL_DIR}/${MAIL_HOSTNAME}-key.pem"
SERVER_CERT="${SSL_DIR}/${MAIL_HOSTNAME}-cert.pem"

if [[ -f "${SERVER_KEY}" && -f "${SERVER_CERT}" && -f "${CA_CERT}" ]]; then
  skip "Mailserver TLS certs"
else
  echo "  Generating self-signed TLS certs for ${MAIL_HOSTNAME}..."
  mkdir -p "${CA_DIR}"

  # 1. CA private key + self-signed certificate
  openssl genrsa -out "${CA_KEY}" 2048 2>/dev/null
  openssl req -new -x509 -days 3650 \
    -key "${CA_KEY}" \
    -out "${CA_CERT}" \
    -subj "/CN=Dev CA/O=Blueshell Dev/C=NL" 2>/dev/null

  # 2. Server private key + CSR
  openssl genrsa -out "${SERVER_KEY}" 2048 2>/dev/null
  CSR_TMP="$(mktemp)"
  openssl req -new \
    -key "${SERVER_KEY}" \
    -out "${CSR_TMP}" \
    -subj "/CN=${MAIL_HOSTNAME}/O=Blueshell Dev/C=NL" 2>/dev/null

  # 3. Sign server cert with CA, add SANs so IMAP clients accept it
  EXT_TMP="$(mktemp)"
  cat > "${EXT_TMP}" <<EOF
[v3_req]
subjectAltName = DNS:${MAIL_HOSTNAME},DNS:mailserver,DNS:localhost
EOF
  openssl x509 -req -days 3650 \
    -in "${CSR_TMP}" \
    -CA "${CA_CERT}" -CAkey "${CA_KEY}" -CAcreateserial \
    -out "${SERVER_CERT}" \
    -extfile "${EXT_TMP}" -extensions v3_req 2>/dev/null

  rm -f "${CSR_TMP}" "${EXT_TMP}"

  ok "Generated CA cert:     ${CA_CERT}"
  ok "Generated server key:  ${SERVER_KEY}"
  ok "Generated server cert: ${SERVER_CERT}"
fi

echo ""
echo "==> Done. Start the dev environment with:"
echo ""
echo "    docker compose -f docker-compose.dev.yml up"
echo ""
