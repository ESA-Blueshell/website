#!/usr/bin/env bash
# =============================================================================
# reinstall-vps.sh — reinstall an existing Contabo VPS with a fresh image +
#                    cloud-init user-data.
#
# Prerequisites:
#   1. cntb CLI installed  (https://github.com/contabo/cntb)
#   2. cloud-config.yaml rendered  (run vps/cloud-init/render.sh first)
#   3. Credentials + instance ID in vps/.env  (see .example.env)
#
# Usage:
#   ./reinstall-vps.sh
#
# All configuration is read from vps/.env.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLOUD_CONFIG="${SCRIPT_DIR}/../cloud-init/cloud-config.yaml"
ADMIN_KEY="${HOME}/.ssh/blueshell-admin"

# ── Source .env ───────────────────────────────────────────────────────────────
ENV_FILE="${SCRIPT_DIR}/../.env"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  set -a; source "${ENV_FILE}"; set +a
fi

# ── Validate required variables ───────────────────────────────────────────────
: "${CLIENT_ID:?Set CLIENT_ID in vps/.env (Contabo OAuth2 client ID)}"
: "${CLIENT_SECRET:?Set CLIENT_SECRET in vps/.env (Contabo OAuth2 client secret)}"
: "${CONTABO_API_USER:?Set CONTABO_API_USER in vps/.env (Contabo login email)}"
: "${CONTABO_API_PASSWORD:?Set CONTABO_API_PASSWORD in vps/.env (Contabo login password)}"
: "${CONTABO_INSTANCE_ID:?Set CONTABO_INSTANCE_ID in vps/.env (numeric instance ID, e.g. 203162642)}"
: "${CONTABO_SSH_KEY_ID:?Set CONTABO_SSH_KEY_ID in vps/.env (run: cntb get secrets --secretType ssh)}"

# ── Check cloud-config is rendered ───────────────────────────────────────────
if [[ ! -f "${CLOUD_CONFIG}" ]]; then
  echo "Error: ${CLOUD_CONFIG} not found." >&2
  echo "Run first:  cd vps && ./cloud-init/render.sh" >&2
  exit 1
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo "==> Reinstalling Contabo VPS"
echo "    Instance ID: ${CONTABO_INSTANCE_ID}"
echo "    Image:       debian-13 (0a3f4b06-a104-4917-bc85-11eba40cb6de)"
echo "    SSH key ID:  ${CONTABO_SSH_KEY_ID}"
echo "    User-data:   ${CLOUD_CONFIG}"
echo ""
echo "WARNING: This will WIPE all data on instance ${CONTABO_INSTANCE_ID}."
read -r -p "Type 'yes' to confirm: " CONFIRM
if [[ "${CONFIRM}" != "yes" ]]; then
  echo "Aborted."
  exit 1
fi
echo ""

# ── Reinstall the instance ────────────────────────────────────────────────────
cntb reinstall instance "${CONTABO_INSTANCE_ID}" \
  --oauth2-clientid      "${CLIENT_ID}" \
  --oauth2-client-secret "${CLIENT_SECRET}" \
  --oauth2-user          "${CONTABO_API_USER}" \
  --oauth2-password      "${CONTABO_API_PASSWORD}" \
  --imageId              "0a3f4b06-a104-4917-bc85-11eba40cb6de" \
  --sshKeys              "${CONTABO_SSH_KEY_ID}" \
  --userData             "$(cat "${CLOUD_CONFIG}")"

echo ""
echo "Reinstall requested. The VPS IP address remains the same: check vps/.env REMOTE_HOST."
echo ""
echo "Cloud-init will run on first boot (~8–12 min). Once complete:"
echo "  ssh -p 2222 -i ${ADMIN_KEY} admin@\${REMOTE_HOST}"
echo ""
echo "Then SSH in as the website user and deploy:"
echo "  ssh -p 2222 -i ~/.ssh/blueshell-website website@\${REMOTE_HOST}"
echo "  website up"