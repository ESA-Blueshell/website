#!/usr/bin/env bash
# =============================================================================
# create-vps.sh — provision a new Contabo VPS with the standalone cloud-init.
#
# Prerequisites:
#   1. cntb CLI installed  (https://github.com/contabo/cntb)
#   2. SSH keypairs exist  (run image/cloud-init/render.sh --standalone first)
#   3. Admin SSH public key (blueshell-admin.pub) uploaded to Contabo and its ID set in CONTABO_SSH_KEY_ID
#      (see helper step printed by this script if the key is not yet uploaded)
#   4. Credentials in image/.env  (see .example.env)
#
# Usage:
#   ./create-vps.sh
#
# All configuration is read from image/.env.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLOUD_CONFIG="${SCRIPT_DIR}/../cloud-init/cloud-config-standalone.yaml"
ADMIN_KEY="${HOME}/.ssh/blueshell-admin"

# ── Source .env ───────────────────────────────────────────────────────────────
ENV_FILE="${SCRIPT_DIR}/../.env"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  set -a; source "${ENV_FILE}"; set +a
fi

# ── Validate required variables ───────────────────────────────────────────────
: "${CLIENT_ID:?Set CLIENT_ID in image/.env (Contabo OAuth2 client ID)}"
: "${CLIENT_SECRET:?Set CLIENT_SECRET in image/.env (Contabo OAuth2 client secret)}"
: "${CONTABO_API_USER:?Set CONTABO_API_USER in image/.env (Contabo login email)}"
: "${CONTABO_API_PASSWORD:?Set CONTABO_API_PASSWORD in image/.env (Contabo login password)}"
: "${CONTABO_PRODUCT_ID:?Set CONTABO_PRODUCT_ID in image/.env (e.g. V22 — run: cntb get products)}"
: "${CONTABO_REGION:?Set CONTABO_REGION in image/.env (EUROPE | US_CENTRAL | US_EAST | US_WEST | ASIA | AUSTRALIA)}"
: "${CONTABO_SSH_KEY_ID:?Set CONTABO_SSH_KEY_ID in image/.env (run: cntb get secrets --secretType ssh)}"

# ── Check cloud-config is rendered ───────────────────────────────────────────
if [[ ! -f "${CLOUD_CONFIG}" ]]; then
  echo "Error: ${CLOUD_CONFIG} not found." >&2
  echo "Run first:  cd image && ./cloud-init/render.sh --standalone" >&2
  exit 1
fi

# ── Hint: upload SSH key if CONTABO_SSH_KEY_ID looks like a placeholder ───────
if [[ "${CONTABO_SSH_KEY_ID}" == "12345" || "${CONTABO_SSH_KEY_ID}" == "0" ]]; then
  echo "WARNING: CONTABO_SSH_KEY_ID looks like a placeholder."
  echo "Upload your admin SSH public key first:"
  echo ""
  echo "  cntb create secret \\"
  echo "    --oauth2ClientId '${CLIENT_ID}' \\"
  echo "    --oauth2ClientSecret '${CLIENT_SECRET}' \\"
  echo "    --oauth2User '${CONTABO_API_USER}' \\"
  echo "    --oauth2Password '${CONTABO_API_PASSWORD}' \\"
  echo "    --name blueshell-admin \\"
  echo "    --value \"\$(cat ${ADMIN_KEY}.pub)\" \\"
  echo "    --secretType ssh"
  echo ""
  echo "Then set the returned ID as CONTABO_SSH_KEY_ID in image/.env and re-run."
  exit 1
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo "==> Creating Contabo VPS"
echo "    Product:    ${CONTABO_PRODUCT_ID}"
echo "    Region:     ${CONTABO_REGION}"
echo "    Image:      debian-12"
echo "    SSH key ID: ${CONTABO_SSH_KEY_ID}"
echo "    User-data:  ${CLOUD_CONFIG}"
echo ""

# ── Create the instance ───────────────────────────────────────────────────────
# Prefer --userDataFile if supported (avoids shell-quoting an 18KB string).
# Falls back to --userData with the file contents if the flag is unavailable.
if cntb create instance --help 2>&1 | grep -q 'userDataFile'; then
  cntb create instance \
    --oauth2ClientId     "${CLIENT_ID}" \
    --oauth2ClientSecret "${CLIENT_SECRET}" \
    --oauth2User         "${CONTABO_API_USER}" \
    --oauth2Password     "${CONTABO_API_PASSWORD}" \
    --productId          "${CONTABO_PRODUCT_ID}" \
    --region             "${CONTABO_REGION}" \
    --imageId            "debian-12" \
    --sshKeys            "${CONTABO_SSH_KEY_ID}" \
    --userDataFile       "${CLOUD_CONFIG}"
else
  cntb create instance \
    --oauth2ClientId     "${CLIENT_ID}" \
    --oauth2ClientSecret "${CLIENT_SECRET}" \
    --oauth2User         "${CONTABO_API_USER}" \
    --oauth2Password     "${CONTABO_API_PASSWORD}" \
    --productId          "${CONTABO_PRODUCT_ID}" \
    --region             "${CONTABO_REGION}" \
    --imageId            "debian-12" \
    --sshKeys            "${CONTABO_SSH_KEY_ID}" \
    --userData           "$(cat "${CLOUD_CONFIG}")"
fi

echo ""
echo "VPS creation requested. Check the Contabo portal for the assigned IP address."
echo ""
echo "Cloud-init will run on first boot (~8–12 min). Once complete:"
echo "  ssh -p 2222 -i ${ADMIN_KEY} admin@<IP>"
echo ""
echo "Then SSH in as the website user and deploy:"
echo "  ssh -p 2222 -i ~/.ssh/blueshell-website website@<IP>"
echo "  website up"
