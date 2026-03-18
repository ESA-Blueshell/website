#!/usr/bin/env bash
# =============================================================================
# scripts/rotate-listmonk-credentials.sh — Rotate Listmonk admin password and
# regenerate the API token written to the listmonk-secrets volume.
#
# Usage:
#   bash scripts/rotate-listmonk-credentials.sh [stack-name]
#   stack-name: website (default, prod) | website-staging | website-dev
#
# Required environment variables:
#   INFISICAL_TOKEN       — machine identity access token
#   INFISICAL_PROJECT_ID  — Infisical project ID
# =============================================================================
set -euo pipefail

STACK="${1:-website}"
ENV="$([ "$STACK" = "website" ] && echo "prod" || echo "${STACK#website-}")"

LISTMONK_URL="http://localhost:9000"

NEW_ADMIN_PW="$(openssl rand -base64 32)"

echo "==> Fetching current Listmonk credentials from Infisical..."
OLD_ADMIN_USER="$(infisical secrets get LISTMONK_ADMIN_USERNAME \
  --token="${INFISICAL_TOKEN:?INFISICAL_TOKEN required}" \
  --projectId="${INFISICAL_PROJECT_ID:?INFISICAL_PROJECT_ID required}" \
  --env="${ENV}" \
  --plain)"
OLD_ADMIN_PW="$(infisical secrets get LISTMONK_ADMIN_PASSWORD \
  --token="${INFISICAL_TOKEN}" \
  --projectId="${INFISICAL_PROJECT_ID}" \
  --env="${ENV}" \
  --plain)"

echo "==> Updating Listmonk admin password via API..."
# Authenticate and update password via Listmonk admin API
SESSION=$(curl -sc /tmp/lm-cookie "${LISTMONK_URL}/admin/login" \
  -d "username=${OLD_ADMIN_USER}&password=${OLD_ADMIN_PW}" -L)
curl -sb /tmp/lm-cookie -X PUT "${LISTMONK_URL}/api/profile" \
  -H "Content-Type: application/json" \
  -d "{\"password\":\"${NEW_ADMIN_PW}\",\"password_confirm\":\"${NEW_ADMIN_PW}\"}"
rm -f /tmp/lm-cookie

echo "==> Updating LISTMONK_ADMIN_PASSWORD in Infisical..."
infisical secrets set "LISTMONK_ADMIN_PASSWORD=${NEW_ADMIN_PW}" \
  --token="${INFISICAL_TOKEN}" \
  --projectId="${INFISICAL_PROJECT_ID}" \
  --env="${ENV}"

echo "==> Removing old API token so listmonk-setup regenerates it on redeploy..."
docker exec "$(docker ps -q -f name="${STACK}_listmonk-db")" \
  psql -U listmonk -c "DELETE FROM users WHERE username='api' AND type='api';"
docker volume rm "${STACK}_listmonk-secrets" 2>/dev/null || true

echo "==> Redeploying listmonk-setup to regenerate API token..."
docker service update --force "${STACK}_listmonk-setup"
docker service update --force "${STACK}_api"
echo "==> Listmonk credentials rotated."
